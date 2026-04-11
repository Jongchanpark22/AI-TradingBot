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