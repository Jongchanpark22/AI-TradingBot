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
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        if (availableBalance == null || availableBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // 기준: 전체 자산(totalBalance) 기준 비율로 계산, 실제 사용 가능 원화로 상한
        BigDecimal totalBalance = account.getTotalBalance() != null
                && account.getTotalBalance().compareTo(BigDecimal.ZERO) > 0
                ? account.getTotalBalance() : availableBalance;

        BigDecimal riskPerTrade = params.getRiskPerTrade() != null
                ? params.getRiskPerTrade() : new BigDecimal("0.20");
        BigDecimal maxPercent = params.getMaxOrderPercent() != null
                ? params.getMaxOrderPercent() : new BigDecimal("0.25");
        BigDecimal minAmount = params.getMinOrderAmount() != null
                ? params.getMinOrderAmount() : new BigDecimal("10000");

        // 기본 20%, 최대 25% (전체 자산 기준)
        BigDecimal baseAmount = totalBalance.multiply(riskPerTrade).min(availableBalance);   // 20%
        BigDecimal maxAmount  = totalBalance.multiply(maxPercent).min(availableBalance);     // 25%

        BigDecimal orderAmount = switch (signal.getSignal()) {
            case STRONG_BUY -> maxAmount;                                  // 25%
            case BUY        -> baseAmount;                                 // 20%
            case WEAK_BUY   -> baseAmount.multiply(new BigDecimal("0.7")); // ~14%
            default         -> BigDecimal.ZERO;
        };

        // 25% 상한 적용
        orderAmount = orderAmount.min(maxAmount);

        // 최소 주문금액 10,000 KRW
        if (orderAmount.compareTo(minAmount) < 0) {
            if (availableBalance.compareTo(minAmount) >= 0) {
                return minAmount;
            }
            return BigDecimal.ZERO;
        }

        return orderAmount;
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
        double totalBalance = toDouble(account.getTotalBalance());
        double availableBalance = toDouble(account.getAvailableBalance());
        EntryPlan plan = riskManager.planLong(totalBalance, currentPrice, atr);
        if (!plan.isExecutable()) return null;
        if (plan.notional() > availableBalance) {
            // not enough cash to fund the planned size — scale down to available
            double cappedQty = availableBalance / currentPrice;
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
                .price(BigDecimal.valueOf(currentPrice))
                .quantity(BigDecimal.valueOf(plan.quantity()))
                .status(Order.OrderStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(plan.notional()))
                .remark(String.format(
                        "EntryATR sl=%.2f tp=%.2f risk=%.2f rr=%.2f",
                        plan.stopLossPrice(), plan.takeProfitPrice(),
                        plan.riskAmount(), plan.riskRewardRatio()))
                .build();
    }

    private static double toDouble(BigDecimal v) {
        return v == null ? 0.0 : v.doubleValue();
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
        BigDecimal entryBd = position.getAvgBuyPrice();
        if (stop == null || initialStop == null || entryBd == null) return null;
        double entry = entryBd.doubleValue();

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
                    .price(BigDecimal.valueOf(currentPrice))
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
                    .price(BigDecimal.valueOf(currentPrice))
                    .quantity(position.getQuantity().divide(BigDecimal.valueOf(2), RoundingMode.DOWN))
                    .status(Order.OrderStatus.PENDING)
                    .remark("PartialExit:+1R")
                    .build();
        }
        return null;
    }

    // ====== 거래 매개변수 ======

    @Builder
    @Data
    public static class TradingParameters {
        /** 기본 투자 비율 — 총 자산의 25% */
        @Builder.Default
        private BigDecimal riskPerTrade = new BigDecimal("0.25");

        /** 최대 투자 비율 — 총 자산의 30% (STRONG_BUY 시 적용) */
        @Builder.Default
        private BigDecimal maxOrderPercent = new BigDecimal("0.30");

        /** 최소 주문 금액 (KRW) */
        @Builder.Default
        private BigDecimal minOrderAmount = new BigDecimal("20000");

        @Builder.Default
        private BigDecimal stopLossPercent = new BigDecimal("2.5");

        @Builder.Default
        private BigDecimal takeProfitPercent = new BigDecimal("7.0");

        @Builder.Default
        private BigDecimal maxDailyLoss = new BigDecimal("10.0");

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
