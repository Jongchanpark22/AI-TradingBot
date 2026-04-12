package com.example.cryptobot.order;

import com.example.cryptobot.exchange.upbit.client.UpbitApiClient;
import com.example.cryptobot.exchange.upbit.dto.UpbitOrderDto;
import com.example.cryptobot.portfolio.Position;
import com.example.cryptobot.portfolio.PositionRepository;
import com.example.cryptobot.strategy.monitor.PositionMonitor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 업비트 실제 주문 상태를 주기적으로 조회해 로컬 DB와 동기화하는 서비스.
 *
 * <p>PENDING / PARTIALLY_FILLED 상태이면서 exchangeOrderId(업비트 UUID)가 있는
 * 주문만 대상으로 한다. 매 1분마다 실행된다.</p>
 *
 * <ul>
 *   <li>wait / watch  → PENDING (변동 없음)</li>
 *   <li>done          → FILLED → 매수면 포지션 생성, 매도면 포지션 종료</li>
 *   <li>cancel        → CANCELLED</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSyncService {

    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final UpbitApiClient upbitApiClient;
    private final ApplicationContext applicationContext;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void syncPendingOrders() {
        List<Order> orders = orderRepository.findByStatusInAndExchangeOrderIdIsNotNull(
                List.of(Order.OrderStatus.PENDING, Order.OrderStatus.PARTIALLY_FILLED));

        if (orders.isEmpty()) return;

        log.info("[OrderSync] 미체결 주문 동기화 시작: {}건", orders.size());

        int updated = 0;
        for (Order order : orders) {
            try {
                if (syncOrder(order)) updated++;
            } catch (Exception e) {
                log.error("[OrderSync] 동기화 실패: id={}, uuid={}", order.getId(), order.getExchangeOrderId(), e);
            }
        }

        if (updated > 0) {
            log.info("[OrderSync] 상태 변경 완료: {}건", updated);
        }
    }

    private boolean syncOrder(Order order) {
        UpbitOrderDto dto = upbitApiClient.getOrderStatus(order.getExchangeOrderId());
        if (dto == null || dto.getState() == null) return false;

        Order.OrderStatus newStatus = mapState(dto.getState());
        if (newStatus == null || newStatus == order.getStatus()) return false;

        BigDecimal executedVolume = dto.getExecutedVolume() != null ? dto.getExecutedVolume() : BigDecimal.ZERO;
        BigDecimal paidFee        = dto.getPaidFee()        != null ? dto.getPaidFee()        : BigDecimal.ZERO;

        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        order.setFilledQuantity(executedVolume);
        order.setFee(paidFee);
        orderRepository.save(order);

        log.info("[OrderSync] 주문 상태 변경: id={}, uuid={}, {} → {}",
                order.getId(), order.getExchangeOrderId(), oldStatus, newStatus);

        // 체결 완료 시 포지션 반영
        if (newStatus == Order.OrderStatus.FILLED) {
            if (order.getSide() == Order.OrderSide.BUY) {
                openPosition(order, executedVolume);
            } else if (order.getSide() == Order.OrderSide.SELL) {
                closePosition(order);
            }
        }

        return true;
    }

    /**
     * 매수 체결 → 포지션 생성 (이미 OPEN 포지션이 있으면 수량/평균가 업데이트).
     * 생성/갱신 후 PositionMonitor에 등록해 실시간 손절이 즉시 활성화된다.
     */
    private void openPosition(Order order, BigDecimal filledQty) {
        if (filledQty == null || filledQty.compareTo(BigDecimal.ZERO) <= 0) return;

        Optional<Position> existing = positionRepository
                .findByAccountAndSymbolAndStatus(order.getAccount(), order.getSymbol(), Position.PositionStatus.OPEN);

        Position pos;
        if (existing.isPresent()) {
            // 추가 매수 — 수량 합산, 평균가 재계산
            pos = existing.get();
            BigDecimal prevQty   = pos.getQuantity()    != null ? pos.getQuantity()    : BigDecimal.ZERO;
            BigDecimal prevPrice = pos.getAvgBuyPrice() != null ? pos.getAvgBuyPrice() : BigDecimal.ZERO;

            BigDecimal newQty      = prevQty.add(filledQty);
            BigDecimal newAvgPrice = prevQty.multiply(prevPrice)
                    .add(filledQty.multiply(order.getPrice()))
                    .divide(newQty, 2, java.math.RoundingMode.HALF_UP);

            pos.setQuantity(newQty);
            pos.setAvgBuyPrice(newAvgPrice);
            pos = positionRepository.save(pos);
            log.info("[OrderSync] 포지션 추가 매수: symbol={}, qty={}, avgPrice={}", order.getSymbol(), newQty, newAvgPrice);
        } else {
            // 신규 포지션 생성
            pos = Position.builder()
                    .account(order.getAccount())
                    .symbol(order.getSymbol())
                    .quantity(filledQty)
                    .avgBuyPrice(order.getPrice())
                    .currentPrice(order.getPrice())
                    .unrealizedProfit(BigDecimal.ZERO)
                    .unrealizedProfitRate(BigDecimal.ZERO)
                    .status(Position.PositionStatus.OPEN)
                    .build();
            pos = positionRepository.save(pos);
            log.info("[OrderSync] 포지션 생성: symbol={}, qty={}, avgPrice={}",
                    order.getSymbol(), filledQty, order.getPrice());
        }

        // PositionMonitor에 등록 — 실시간 손절/트레일링 즉시 활성화
        try {
            applicationContext.getBean(PositionMonitor.class).trackPosition(pos);
        } catch (Exception e) {
            log.warn("[OrderSync] PositionMonitor 등록 실패: symbol={}", order.getSymbol(), e);
        }
    }

    /**
     * 매도 체결 → 해당 심볼의 OPEN 포지션 종료.
     */
    private void closePosition(Order order) {
        positionRepository.findByAccountAndSymbolAndStatus(
                order.getAccount(), order.getSymbol(), Position.PositionStatus.OPEN)
                .ifPresent(pos -> {
                    pos.setStatus(Position.PositionStatus.CLOSED);
                    pos.setUnrealizedProfit(BigDecimal.ZERO);
                    pos.setUnrealizedProfitRate(BigDecimal.ZERO);
                    positionRepository.save(pos);
                    log.info("[OrderSync] 포지션 종료: symbol={}", order.getSymbol());
                });
    }

    private Order.OrderStatus mapState(String upbitState) {
        return switch (upbitState) {
            case "wait", "watch" -> Order.OrderStatus.PENDING;
            case "done"          -> Order.OrderStatus.FILLED;
            case "cancel"        -> Order.OrderStatus.CANCELLED;
            default -> null;
        };
    }
}
