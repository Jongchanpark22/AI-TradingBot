package com.example.cryptobot.strategy.hybrid;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.order.Order;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TradeExecutionEngine {

    public Order executeBySignal(
            Account account,
            String symbol,
            BigDecimal currentPrice,
            HybridSignalAnalyzer.TradeSignal signal,
            TradingParameters params) {

        BigDecimal orderAmount = calculateOrderAmount(account, signal, params);
        if (orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        if (currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal quantity = orderAmount.divide(currentPrice, 8, RoundingMode.DOWN);

        return Order.builder()
                .account(account)
                .symbol(symbol)
                .type(Order.OrderType.LIMIT)
                .side(Order.OrderSide.BUY)
                .price(currentPrice)
                .quantity(quantity)
                .status(Order.OrderStatus.PENDING)
                .totalAmount(orderAmount)
                .build();
    }

    public Order executeSellSignal(
            Account account,
            String symbol,
            BigDecimal currentPrice,
            BigDecimal quantity,
            HybridSignalAnalyzer.TradeSignal signal) {

        return Order.builder()
                .account(account)
                .symbol(symbol)
                .type(Order.OrderType.LIMIT)
                .side(Order.OrderSide.SELL)
                .price(currentPrice)
                .quantity(quantity)
                .status(Order.OrderStatus.PENDING)
                .totalAmount(currentPrice.multiply(quantity))
                .build();
    }

    public Order executeStopLoss(
            Account account,
            String symbol,
            BigDecimal stopLossPrice,
            BigDecimal quantity) {

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

    public Order executeTakeProfit(
            Account account,
            String symbol,
            BigDecimal takeProfitPrice,
            BigDecimal quantity) {

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

    private BigDecimal calculateOrderAmount(
            Account account,
            HybridSignalAnalyzer.TradeSignal signal,
            TradingParameters params) {

        BigDecimal availableBalance = account.getAvailableBalance();
        BigDecimal totalBalance = account.getTotalBalance();

        if (availableBalance == null || totalBalance == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal baseAmount = totalBalance.multiply(params.getRiskPerTrade());
        baseAmount = baseAmount.min(availableBalance);

        return switch (signal.getSignal()) {
            case STRONG_BUY -> baseAmount;
            case BUY -> baseAmount.multiply(new BigDecimal("0.7"));
            case WEAK_BUY -> baseAmount.multiply(new BigDecimal("0.4"));
            default -> BigDecimal.ZERO;
        };
    }

    @Builder
    @Data
    public static class TradingParameters {
        @Builder.Default
        private BigDecimal riskPerTrade = new BigDecimal("0.03");

        @Builder.Default
        private BigDecimal stopLossPercent = new BigDecimal("2.5");

        @Builder.Default
        private BigDecimal takeProfitPercent = new BigDecimal("6.75");

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