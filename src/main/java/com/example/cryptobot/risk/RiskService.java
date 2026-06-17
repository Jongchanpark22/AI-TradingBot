package com.example.cryptobot.risk;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.order.Order;
import com.example.cryptobot.order.OrderRepository;
import com.example.cryptobot.portfolio.Position;
import com.example.cryptobot.portfolio.PositionRepository;
import com.example.cryptobot.trade.TradeHistory;
import com.example.cryptobot.trade.TradeHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RiskService {

    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;
    private final RiskRecordRepository riskRecordRepository;
    private final TradeHistoryRepository tradeHistoryRepository;

    private static final int STOP_LOSS_COOLDOWN_HOURS = 6;

    public RiskCheckResult validateBuy(
            Account account,
            String symbol,
            BigDecimal maxDailyLoss,
            int maxOpenPositions
    ) {

        // 손절 후 6시간 쿨다운: 같은 코인 재진입 차단 (복수매매 방지)
        LocalDateTime cooldownThreshold = LocalDateTime.now().minusHours(STOP_LOSS_COOLDOWN_HOURS);
        if (tradeHistoryRepository.existsByAccountIdAndSymbolAndExitTypeAndExitTimeAfter(
                account.getId(), symbol, TradeHistory.ExitType.STOP_LOSS, cooldownThreshold)) {
            return RiskCheckResult.deny("손절 후 쿨다운 중 (" + STOP_LOSS_COOLDOWN_HOURS + "시간)");
        }

        Optional<Position> existingPosition = positionRepository
                .findActiveByAccountAndSymbol(account, symbol);

        if (existingPosition.isPresent()) {
            return RiskCheckResult.deny("이미 오픈 포지션 존재");
        }

        List<Order> pendingOrders = orderRepository.findByAccountAndSymbolAndStatusIn(
                account,
                symbol,
                List.of(Order.OrderStatus.PENDING, Order.OrderStatus.PARTIALLY_FILLED)
        );

        if (!pendingOrders.isEmpty()) {
            return RiskCheckResult.deny("미체결 주문 존재");
        }

        long openPositionCount = positionRepository.countActiveByAccount(account);

        if (openPositionCount >= maxOpenPositions) {
            return RiskCheckResult.deny("최대 오픈 포지션 수 초과");
        }

        Optional<RiskRecord> riskRecordOpt =
                riskRecordRepository.findByAccountAndTradeDate(account, LocalDate.now());

        if (riskRecordOpt.isPresent()) {
            RiskRecord riskRecord = riskRecordOpt.get();

            if (Boolean.FALSE.equals(riskRecord.getIsTradingAllowed())) {
                return RiskCheckResult.deny("오늘 거래 중지 상태");
            }

            BigDecimal dailyLoss = riskRecord.getDailyLoss();

            if (maxDailyLoss != null
                    && dailyLoss != null
                    && dailyLoss.compareTo(maxDailyLoss) >= 0) {
                return RiskCheckResult.deny("일일 최대 손실 초과");
            }
        }

        return RiskCheckResult.permit();
    }

    public record RiskCheckResult(boolean allowed, String reason) {
        public static RiskCheckResult permit() {
            return new RiskCheckResult(true, null);
        }

        public static RiskCheckResult deny(String reason) {
            return new RiskCheckResult(false, reason);
        }
    }
}