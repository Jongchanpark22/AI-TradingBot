package com.example.cryptobot.strategy.hybrid;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.order.Order;
import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.portfolio.Position;
import com.example.cryptobot.strategy.indicator.Indicators;
import com.example.cryptobot.strategy.risk.EntryPlan;
import com.example.cryptobot.strategy.risk.RiskManager;
import com.example.cryptobot.strategy.risk.TrailingDecision;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Optional;

/**
 * 거래 실행 엔진
 * - 진입 신호 → 매수 주문 생성
 * - 청산 신호 → 매도 주문 생성
 * - 손절/익절 자동 관리
 */
@Data
public class TradeExecutionEngine {

    public Order executeBySignal(
            Account account,
            String symbol,
            BigDecimal currentPrice,
            HybridSignalAnalyzer.TradeSignal signal,
            TradingParameters params) {

        if (account == null || symbol == null || symbol.isBlank() || signal == null || params == null) {
            return null;
        }

        BigDecimal orderAmount = calculateOrderAmount(account, signal, params);
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal quantity = orderAmount.divide(currentPrice, 8, RoundingMode.DOWN);

        // 소수점 8자리 내림 후 수량이 0이면 유효하지 않은 주문이므로 생성하지 않음
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        // 실제 주문 수량 기준 금액으로 저장
        BigDecimal totalAmount = currentPrice.multiply(quantity);

        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        return Order.builder()
                .account(account)
                .symbol(symbol)
                .type(Order.OrderType.LIMIT)
                .side(Order.OrderSide.BUY)
                .price(currentPrice)
                .quantity(quantity)
                .filledQuantity(BigDecimal.ZERO)
                .filledAmount(BigDecimal.ZERO)
                .fee(BigDecimal.ZERO)
                .status(Order.OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .build();
    }

    public Order executeSellSignal(
            Account account,
            String symbol,
            BigDecimal currentPrice,
            BigDecimal quantity,
            HybridSignalAnalyzer.TradeSignal signal) {

        if (account == null || symbol == null || symbol.isBlank()) {
            return null;
        }

        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal totalAmount = currentPrice.multiply(quantity);
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        return Order.builder()
                .account(account)
                .symbol(symbol)
                .type(Order.OrderType.LIMIT)
                .side(Order.OrderSide.SELL)
                .price(currentPrice)
                .quantity(quantity)
                .filledQuantity(BigDecimal.ZERO)
                .filledAmount(BigDecimal.ZERO)
                .fee(BigDecimal.ZERO)
                .status(Order.OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .build();
    }

    public Order executeStopLoss(
            Account account,
            String symbol,
            BigDecimal stopLossPrice,
            BigDecimal quantity) {

        if (account == null || symbol == null || symbol.isBlank()) {
            return null;
        }

        if (stopLossPrice == null || stopLossPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        return Order.builder()
                .account(account)
                .symbol(symbol)
                .type(Order.OrderType.MARKET)
                .side(Order.OrderSide.SELL)
                .price(stopLossPrice)
                .quantity(quantity)
                .filledQuantity(BigDecimal.ZERO)
                .filledAmount(BigDecimal.ZERO)
                .fee(BigDecimal.ZERO)
                .status(Order.OrderStatus.PENDING)
                .remark("StopLoss")
                .build();
    }

    public Order executeTakeProfit(
            Account account,
            String symbol,
            BigDecimal takeProfitPrice,
            BigDecimal quantity) {

        if (account == null || symbol == null || symbol.isBlank()) {
            return null;
        }

        if (takeProfitPrice == null || takeProfitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        return Order.builder()
                .account(account)
                .symbol(symbol)
                .type(Order.OrderType.LIMIT)
                .side(Order.OrderSide.SELL)
                .price(takeProfitPrice)
                .quantity(quantity)
                .filledQuantity(BigDecimal.ZERO)
                .filledAmount(BigDecimal.ZERO)
                .fee(BigDecimal.ZERO)
                .status(Order.OrderStatus.PENDING)
                .remark("TakeProfit")
                .build();
    }

    private BigDecimal calculateOrderAmount(
            Account account,
            HybridSignalAnalyzer.TradeSignal signal,
            TradingParameters params) {

        if (account == null || signal == null || params == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal availableBalance = account.getAvailableBalance();
        BigDecimal totalBalance = account.getTotalBalance();

        if (availableBalance == null || totalBalance == null) {
            return BigDecimal.ZERO;
        }

        if (availableBalance.compareTo(BigDecimal.ZERO) <= 0 || totalBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal riskPerTrade = params.getRiskPerTrade() != null
                ? params.getRiskPerTrade()
                : BigDecimal.ZERO;

        if (riskPerTrade.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal baseAmount = totalBalance.multiply(riskPerTrade);
        baseAmount = baseAmount.min(availableBalance);

        if (baseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return switch (signal.getSignal()) {
            case STRONG_BUY -> baseAmount;
            case BUY -> baseAmount.multiply(new BigDecimal("0.7"));
            case WEAK_BUY -> baseAmount.multiply(new BigDecimal("0.4"));
            default -> BigDecimal.ZERO;
        };
    }

    // ============================================================
    // ATR / RiskManager based execution (Phase 2)
    // ============================================================

    /**
     * ATR-based entry: sizes the position from current ATR and the
     * configured per-trade risk fraction. Replaces the legacy fixed-percent
     * sizing. Returns {@code null} when the {@link EntryPlan} is not
     * executable (insufficient data, zero ATR, etc.).
     *
     * @param atrPeriod number of bars used to compute ATR (typically 14)
     */
    public Order executeEntryWithRisk(
            Account account,
            String symbol,
            double currentPrice,
            List<Candle> recentCandles,
            int atrPeriod,
            RiskManager riskManager) {

        if (!riskManager.canOpenAnotherPosition(0 /* caller should pre-filter */)) {
            return null;
        }
        double atr = Indicators.atr(recentCandles, atrPeriod);
        EntryPlan plan = riskManager.planLong(account.getTotalBalance(), currentPrice, atr);
        if (!plan.isExecutable()) return null;
        if (plan.notional() > account.getAvailableBalance()) {
            // not enough cash to fund the planned size — scale down to available
            double cappedQty = account.getAvailableBalance() / currentPrice;
            if (cappedQty <= 0) return null;
            plan = new EntryPlan(cappedQty, currentPrice, plan.stopLossPrice(),
                    plan.takeProfitPrice(), plan.riskAmount(), plan.rewardAmount(),
                    plan.atrAtEntry());
        }

        return Order.builder()
                .account(account)
                .symbol(symbol)
                .type(Order.OrderType.LIMIT)
                .side(Order.OrderSide.BUY)
                .price(currentPrice)
                .quantity(plan.quantity())
                .status(Order.OrderStatus.PENDING)
                .totalAmount(plan.notional())
                .remark(String.format(
                        "EntryATR sl=%.2f tp=%.2f risk=%.2f rr=%.2f",
                        plan.stopLossPrice(), plan.takeProfitPrice(),
                        plan.riskAmount(), plan.riskRewardRatio()))
                .build();
    }

    /**
     * Apply trailing-stop / partial-exit logic to an open position. Mutates
     * the {@link Position} entity in-place when the stop is ratcheted up,
     * and returns the (optional) order the executor should send right now:
     * a flat close on stop-hit, or a 50% partial close at +1R. {@code null}
     * means "no action this tick".
     */
    public Order manageOpenPosition(
            Position position,
            double currentPrice,
            List<Candle> recentCandles,
            int atrPeriod,
            RiskManager riskManager) {

        if (position == null || position.getStatus() != Position.PositionStatus.OPEN) {
            return null;
        }
        Double stop = position.getCurrentStopLoss();
        Double initialStop = position.getInitialStopLoss();
        Double entry = position.getAvgBuyPrice();
        if (stop == null || initialStop == null || entry == null) return null;

        double highest = position.getHighestPriceSinceEntry() == null
                ? entry
                : Math.max(position.getHighestPriceSinceEntry(), currentPrice);
        position.setHighestPriceSinceEntry(highest);

        double atr = Indicators.atr(recentCandles, atrPeriod);
        boolean partialDone = Boolean.TRUE.equals(position.getPartialExitDone());

        TrailingDecision d = riskManager.updateTrailing(
                entry, initialStop, stop, currentPrice, highest, atr, partialDone);

        // ratchet stop in storage
        if (d.newStopLoss() > stop) {
            position.setCurrentStopLoss(d.newStopLoss());
        }

        if (d.shouldExitNow()) {
            return Order.builder()
                    .account(position.getAccount())
                    .symbol(position.getSymbol())
                    .type(Order.OrderType.MARKET)
                    .side(Order.OrderSide.SELL)
                    .price(currentPrice)
                    .quantity(position.getQuantity())
                    .status(Order.OrderStatus.PENDING)
                    .remark("StopHit:" + d.reason())
                    .build();
        }
        if (d.shouldPartialExit()) {
            position.setPartialExitDone(true);
            return Order.builder()
                    .account(position.getAccount())
                    .symbol(position.getSymbol())
                    .type(Order.OrderType.MARKET)
                    .side(Order.OrderSide.SELL)
                    .price(currentPrice)
                    .quantity(position.getQuantity() / 2.0)
                    .status(Order.OrderStatus.PENDING)
                    .remark("PartialExit:+1R")
                    .build();
        }
        return null;
    }

    // ====== 거래 매개변수 ======
    
    @lombok.Builder
    @lombok.Data
    public static class TradingParameters {
        @lombok.Builder.Default
        private double riskPerTrade = 0.03;              // 1회 거래 시 계정의 3% 투자
        @lombok.Builder.Default
        private double stopLossPercent = 2.5;            // 손절: 진입가 대비 2.5%
        @lombok.Builder.Default
        private double takeProfitPercent = 6.75;         // 익절: 진입가 대비 6.75%
        @lombok.Builder.Default
        private double maxDailyLoss = 0.10;              // 1일 최대손실: 계정의 10%
        @lombok.Builder.Default
        private int maxOpenPositions = 3;                // 최대 동시 포지션: 3개
        @lombok.Builder.Default
        private int minVolumeRatio = 130;                // 최소 거래량 비율: 130% (평균 대비)
        @lombok.Builder.Default
        private boolean useStopLoss = true;              // 손절 활성화
        @lombok.Builder.Default
        private boolean useTakeProfit = true;            // 익절 활성화
    }
}

        @Builder.Default
        private int maxOpenPositions = 3;

        @Builder.Default
        private int minVolumeRatio = 130;

        @Builder.Default
        private boolean useStopLoss = true;

        @Builder.Default
        private boolean useTakeProfit = true;
    }
}