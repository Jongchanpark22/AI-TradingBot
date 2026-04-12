package com.example.cryptobot.strategy.paper;

import com.example.cryptobot.order.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaperBrokerTest {

    private static Order buyMarket(String symbol, double qty) {
        return Order.builder()
                .symbol(symbol)
                .type(Order.OrderType.MARKET)
                .side(Order.OrderSide.BUY)
                .quantity(BigDecimal.valueOf(qty))
                .build();
    }

    private static Order sellMarket(String symbol, double qty) {
        return Order.builder()
                .symbol(symbol)
                .type(Order.OrderType.MARKET)
                .side(Order.OrderSide.SELL)
                .quantity(BigDecimal.valueOf(qty))
                .build();
    }

    private static Order buyLimit(String symbol, double price, double qty) {
        return Order.builder()
                .symbol(symbol)
                .type(Order.OrderType.LIMIT)
                .side(Order.OrderSide.BUY)
                .price(BigDecimal.valueOf(price))
                .quantity(BigDecimal.valueOf(qty))
                .build();
    }

    @Test
    void marketBuyDebitsCashAndCreditsHoldings() {
        PaperBroker b = new PaperBroker(new BigDecimal("10000"));
        Order o = b.submit(buyMarket("BTC", 0.5), new BigDecimal("100"));

        assertEquals(Order.OrderStatus.FILLED, o.getStatus());
        assertEquals(0, b.cash().compareTo(new BigDecimal("9950")));
        assertEquals(0, b.holding("BTC").compareTo(new BigDecimal("0.5")));
        assertEquals(1, b.filledOrders().size());
        assertNotNull(o.getExchangeOrderId());
    }

    @Test
    void marketBuyRejectedWhenCashInsufficient() {
        PaperBroker b = new PaperBroker(new BigDecimal("10"));
        Order o = b.submit(buyMarket("BTC", 1), new BigDecimal("100"));
        assertEquals(Order.OrderStatus.REJECTED, o.getStatus());
        assertEquals(0, b.cash().compareTo(new BigDecimal("10")));
        assertEquals(0, b.holding("BTC").signum());
    }

    @Test
    void marketSellRejectedWhenHoldingsInsufficient() {
        PaperBroker b = new PaperBroker(new BigDecimal("1000"));
        Order o = b.submit(sellMarket("BTC", 1), new BigDecimal("100"));
        assertEquals(Order.OrderStatus.REJECTED, o.getStatus());
    }

    @Test
    void roundTripBuyThenSellRealisesPnl() {
        PaperBroker b = new PaperBroker(new BigDecimal("10000"));
        b.submit(buyMarket("BTC", 1), new BigDecimal("100"));
        b.submit(sellMarket("BTC", 1), new BigDecimal("120"));
        assertEquals(0, b.cash().compareTo(new BigDecimal("10020")));
        assertEquals(0, b.holding("BTC").signum());
        assertEquals(2, b.filledOrders().size());
    }

    @Test
    void limitBuyAboveMarketParkedThenFilledOnTickCross() {
        PaperBroker b = new PaperBroker(new BigDecimal("10000"));
        // limit below market — order parks
        Order o = b.submit(buyLimit("BTC", 90, 1), new BigDecimal("100"));
        assertEquals(Order.OrderStatus.PENDING, o.getStatus());
        assertEquals(1, b.openOrders().size());

        // tick at 95 still above limit — no fill
        List<Order> filled = b.onTick("BTC", new BigDecimal("95"));
        assertTrue(filled.isEmpty());

        // tick at 90 crosses — fills
        filled = b.onTick("BTC", new BigDecimal("90"));
        assertEquals(1, filled.size());
        assertEquals(Order.OrderStatus.FILLED, o.getStatus());
        assertEquals(0, o.getPrice().compareTo(new BigDecimal("90")));
        assertEquals(0, b.holding("BTC").compareTo(new BigDecimal("1")));
    }

    @Test
    void cancelRemovesPendingOrder() {
        PaperBroker b = new PaperBroker(new BigDecimal("10000"));
        Order o = b.submit(buyLimit("BTC", 80, 1), new BigDecimal("100"));
        assertTrue(b.cancel(o.getExchangeOrderId()));
        assertEquals(Order.OrderStatus.CANCELLED, o.getStatus());
        assertTrue(b.openOrders().isEmpty());

        // re-cancel returns false
        assertFalse(b.cancel(o.getExchangeOrderId()));
    }

    @Test
    void equityIncludesUnrealisedPositionValue() {
        PaperBroker b = new PaperBroker(new BigDecimal("10000"));
        b.submit(buyMarket("BTC", 1), new BigDecimal("100"));
        // BTC now trades at 150
        BigDecimal eq = b.equity(Map.of("BTC", new BigDecimal("150")));
        // 9900 cash + 1 BTC * 150 = 10050
        assertEquals(0, eq.compareTo(new BigDecimal("10050")));
    }

    @Test
    void equityIgnoresSymbolsWithoutPriceData() {
        PaperBroker b = new PaperBroker(new BigDecimal("10000"));
        b.submit(buyMarket("BTC", 1), new BigDecimal("100"));
        // no price for BTC -> position contributes 0, only cash counted
        BigDecimal eq = b.equity(Map.of());
        assertEquals(0, eq.compareTo(new BigDecimal("9900")));
    }
}
