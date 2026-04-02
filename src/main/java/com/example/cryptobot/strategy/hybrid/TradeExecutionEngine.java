package com.example.cryptobot.strategy.hybrid;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.order.Order;
import com.example.cryptobot.market.candle.Candle;
import lombok.AllArgsConstructor;
import lombok.Data;
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

    // ====== 거래 매개변수 ======
    
    @lombok.Builder
    @lombok.Data
    public static class TradingParameters {
        private double riskPerTrade = 0.03;              // 1회 거래 시 계정의 3% 투자
        private double stopLossPercent = 2.5;            // 손절: 진입가 대비 2.5%
        private double takeProfitPercent = 6.75;         // 익절: 진입가 대비 6.75%
        private double maxDailyLoss = 0.10;              // 1일 최대손실: 계정의 10%
        private int maxOpenPositions = 3;                // 최대 동시 포지션: 3개
        private int minVolumeRatio = 130;                // 최소 거래량 비율: 130% (평균 대비)
        private boolean useStopLoss = true;              // 손절 활성화
        private boolean useTakeProfit = true;            // 익절 활성화
    }
}

