package com.example.cryptobot.exchange.upbit.service;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.exchange.upbit.dto.UpbitOrderDto;
import com.example.cryptobot.order.Order;
import com.example.cryptobot.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UpbitSyncService {

    private final OrderRepository orderRepository;

    public void syncOrderStatus(Account account, UpbitOrderDto upbitOrder) {
        try {
            List<Order> orders = orderRepository.findByAccountId(account.getId());

            orders.stream()
                    .filter(order -> order.getExchangeOrderId() != null
                            && order.getExchangeOrderId().equals(upbitOrder.getUuid()))
                    .findFirst()
                    .ifPresent(localOrder -> {
                        Order.OrderStatus newStatus = mapUpbitStatusToLocalStatus(upbitOrder.getState());
                        localOrder.setStatus(newStatus);

                        if (upbitOrder.getExecutedVolume() != null) {
                            localOrder.setFilledQuantity(upbitOrder.getExecutedVolume());
                        }

                        if (upbitOrder.getExecutedVolume() != null && upbitOrder.getPrice() != null) {
                            localOrder.setFilledAmount(
                                    upbitOrder.getExecutedVolume().multiply(upbitOrder.getPrice())
                            );
                        }

                        if (upbitOrder.getPaidFee() != null) {
                            localOrder.setFee(upbitOrder.getPaidFee());
                        }

                        orderRepository.save(localOrder);
                        log.info("✓ 주문 상태 동기화: {} → {}", upbitOrder.getUuid(), newStatus);
                    });

        } catch (Exception e) {
            log.error("주문 상태 동기화 실패: {}", upbitOrder.getUuid(), e);
        }
    }

    public void syncExecution(Account account, UpbitOrderDto upbitOrder) {
        try {
            List<Order> orders = orderRepository.findByAccountId(account.getId());

            orders.stream()
                    .filter(order -> order.getExchangeOrderId() != null
                            && order.getExchangeOrderId().equals(upbitOrder.getUuid()))
                    .findFirst()
                    .ifPresent(localOrder -> {
                        BigDecimal executedVolume = upbitOrder.getExecutedVolume() != null
                                ? upbitOrder.getExecutedVolume()
                                : BigDecimal.ZERO;

                        BigDecimal totalVolume = localOrder.getQuantity() != null
                                ? localOrder.getQuantity()
                                : BigDecimal.ZERO;

                        localOrder.setFilledQuantity(executedVolume);

                        if (executedVolume.compareTo(totalVolume) >= 0 && totalVolume.compareTo(BigDecimal.ZERO) > 0) {
                            localOrder.setStatus(Order.OrderStatus.FILLED);
                            log.info("✓ 주문 완전 체결: {} ({}/{})",
                                    upbitOrder.getUuid(), executedVolume, totalVolume);
                        } else if (executedVolume.compareTo(BigDecimal.ZERO) > 0) {
                            localOrder.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
                            log.info("✓ 주문 부분 체결: {} ({}/{})",
                                    upbitOrder.getUuid(), executedVolume, totalVolume);
                        }

                        if (upbitOrder.getPrice() != null) {
                            localOrder.setFilledAmount(executedVolume.multiply(upbitOrder.getPrice()));
                        }

                        if (upbitOrder.getPaidFee() != null) {
                            localOrder.setFee(upbitOrder.getPaidFee());
                        }

                        orderRepository.save(localOrder);
                    });

        } catch (Exception e) {
            log.error("체결 반영 실패: {}", upbitOrder.getUuid(), e);
        }
    }

    public void syncCancelledOrder(Account account, String upbitOrderUuid) {
        try {
            List<Order> orders = orderRepository.findByAccountId(account.getId());

            orders.stream()
                    .filter(order -> order.getExchangeOrderId() != null
                            && order.getExchangeOrderId().equals(upbitOrderUuid))
                    .findFirst()
                    .ifPresent(localOrder -> {
                        localOrder.setStatus(Order.OrderStatus.CANCELLED);
                        orderRepository.save(localOrder);

                        log.info("✓ 주문 취소 상태 반영: {}", upbitOrderUuid);
                    });

        } catch (Exception e) {
            log.error("주문 취소 상태 반영 실패: {}", upbitOrderUuid, e);
        }
    }

    private Order.OrderStatus mapUpbitStatusToLocalStatus(String upbitStatus) {
        if (upbitStatus == null) {
            return Order.OrderStatus.PENDING;
        }

        return switch (upbitStatus) {
            case "done" -> Order.OrderStatus.FILLED;
            case "cancel" -> Order.OrderStatus.CANCELLED;
            case "watch" -> Order.OrderStatus.PARTIALLY_FILLED;
            case "wait" -> Order.OrderStatus.PENDING;
            default -> Order.OrderStatus.PENDING;
        };
    }
}