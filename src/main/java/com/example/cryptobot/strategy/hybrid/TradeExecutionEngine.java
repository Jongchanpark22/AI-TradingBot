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

    /**
     * 매수 신호에 따른 주문 생성
     * 
     * @param account 거래 계정
     * @param symbol 암호화폐 심볼
     * @param currentPrice 현재 가격
     * @param signal 거래 신호
     * @return 생성된 주문
     */
    public Order executeBySignal(
            Account account,
            String symbol,
            double currentPrice,
            HybridSignalAnalyzer.TradeSignal signal,
            TradingParameters params) {
        
        // 신호 강도에 따른 주문 수량 결정
        double orderAmount = calculateOrderAmount(account, signal, params);
        
        if (orderAmount <= 0) {
            return null;
        }
        
        // 매수 주문 생성
        Order order = Order.builder()
                .account(account)
                .symbol(symbol)
                .type(Order.OrderType.LIMIT)
                .side(Order.OrderSide.BUY)
                .price(currentPrice)
                .quantity(orderAmount / currentPrice)
                .status(Order.OrderStatus.PENDING)
                .totalAmount(orderAmount)
                .build();
        
        return order;
    }

    /**
     * 포지션 청산
     */
    public Order executeSellSignal(
            Account account,
            String symbol,
            double currentPrice,
            double quantity,
            HybridSignalAnalyzer.TradeSignal signal) {
        
        Order order = Order.builder()
                .account(account)
                .symbol(symbol)
                .type(Order.OrderType.LIMIT)
                .side(Order.OrderSide.SELL)
                .price(currentPrice)
                .quantity(quantity)
                .status(Order.OrderStatus.PENDING)
                .totalAmount(currentPrice * quantity)
                .build();
        
        return order;
    }

    /**
     * 손절 주문 생성
     */
    public Order executeStopLoss(
            Account account,
            String symbol,
            double stopLossPrice,
            double quantity) {
        
        return Order.builder()
                .account(account)
                .symbol(symbol)
                .type(Order.OrderType.MARKET)
                .side(Order.OrderSide.SELL)
                .price(stopLossPrice)
                .quantity(quantity)
                .status(Order.OrderStatus.PENDING)
                .remark("StopLoss")
                .build();
    }

    /**
     * 익절 주문 생성
     */
    public Order executeTakeProfit(
            Account account,
            String symbol,
            double takeProfitPrice,
            double quantity) {
        
        return Order.builder()
                .account(account)
                .symbol(symbol)
                .type(Order.OrderType.LIMIT)
                .side(Order.OrderSide.SELL)
                .price(takeProfitPrice)
                .quantity(quantity)
                .status(Order.OrderStatus.PENDING)
                .remark("TakeProfit")
                .build();
    }

    /**
     * 신호 강도에 따른 주문 수량 계산
     * 
     * STRONG_BUY: 최대 투자액 100%
     * BUY: 투자액 70%
     * WEAK_BUY: 투자액 40%
     */
    private double calculateOrderAmount(
            Account account,
            HybridSignalAnalyzer.TradeSignal signal,
            TradingParameters params) {
        
        // 현재 사용 가능 잔액
        double availableBalance = account.getAvailableBalance();
        
        // 1회 최대 투자 금액 (계정의 2-3%)
        double maxOrderAmount = account.getTotalBalance() * params.getRiskPerTrade();
        
        // 실제 주문 금액 (사용 가능 잔액과 최대값의 최소값)
        double baseAmount = Math.min(availableBalance, maxOrderAmount);
        
        // 신호 강도에 따른 투자 비중 조정
        double orderAmount = 0;
        switch (signal.getSignal()) {
            case STRONG_BUY:
                orderAmount = baseAmount * 1.0;  // 100%
                break;
            case BUY:
                orderAmount = baseAmount * 0.7;  // 70%
                break;
            case WEAK_BUY:
                orderAmount = baseAmount * 0.4;  // 40%
                break;
            default:
                orderAmount = 0;
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

