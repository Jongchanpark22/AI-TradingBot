package com.example.cryptobot.strategy.risk;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeStopServiceTest {

    private static final String SYMBOL = "KRW-BTC";
    private static final BigDecimal QTY = new BigDecimal("0.5");

    @Test
    void placeAndQueryStop() {
        InMemoryExchangeStopService svc = new InMemoryExchangeStopService();
        String id = svc.placeStop(SYMBOL, QTY, new BigDecimal("90000"));

        assertNotNull(id);
        assertEquals(1, svc.activeStopCount());
        Optional<BigDecimal> price = svc.getStopPrice(id);
        assertTrue(price.isPresent());
        assertEquals(0, price.get().compareTo(new BigDecimal("90000")));
    }

    @Test
    void moveStopCancelsOldAndCreatesNew() {
        InMemoryExchangeStopService svc = new InMemoryExchangeStopService();
        String id1 = svc.placeStop(SYMBOL, QTY, new BigDecimal("90000"));

        String id2 = svc.moveStop(id1, SYMBOL, QTY, new BigDecimal("92000"));

        assertNotEquals(id1, id2);
        // old stop is cancelled
        assertTrue(svc.getStopPrice(id1).isEmpty());
        assertEquals(InMemoryExchangeStopService.StopState.CANCELLED,
                svc.findStop(id1).orElseThrow().state());
        // new stop is active at tighter price
        assertEquals(0, svc.getStopPrice(id2).orElseThrow().compareTo(new BigDecimal("92000")));
        assertEquals(1, svc.activeStopCount());
    }

    @Test
    void moveStopThrowsWhenNotFound() {
        InMemoryExchangeStopService svc = new InMemoryExchangeStopService();
        assertThrows(IllegalStateException.class,
                () -> svc.moveStop("NONEXISTENT", SYMBOL, QTY, new BigDecimal("90000")));
    }

    @Test
    void moveStopThrowsWhenAlreadyCancelled() {
        InMemoryExchangeStopService svc = new InMemoryExchangeStopService();
        String id = svc.placeStop(SYMBOL, QTY, new BigDecimal("90000"));
        svc.cancelStop(id);

        assertThrows(IllegalStateException.class,
                () -> svc.moveStop(id, SYMBOL, QTY, new BigDecimal("92000")));
    }

    @Test
    void cancelIsIdempotent() {
        InMemoryExchangeStopService svc = new InMemoryExchangeStopService();
        String id = svc.placeStop(SYMBOL, QTY, new BigDecimal("90000"));

        assertTrue(svc.cancelStop(id));
        assertTrue(svc.cancelStop(id)); // second cancel also returns true
        assertTrue(svc.cancelStop("NEVER_EXISTED")); // unknown id is idempotent
        assertEquals(0, svc.activeStopCount());
    }

    @Test
    void triggerStopChangesStateToTriggered() {
        InMemoryExchangeStopService svc = new InMemoryExchangeStopService();
        String id = svc.placeStop(SYMBOL, QTY, new BigDecimal("90000"));

        Optional<InMemoryExchangeStopService.StopOrder> triggered = svc.triggerStop(id);
        assertTrue(triggered.isPresent());
        assertEquals(InMemoryExchangeStopService.StopState.TRIGGERED, triggered.get().state());
        // no longer active
        assertTrue(svc.getStopPrice(id).isEmpty());
        assertEquals(0, svc.activeStopCount());
    }

    @Test
    void triggerInactiveStopReturnsEmpty() {
        InMemoryExchangeStopService svc = new InMemoryExchangeStopService();
        String id = svc.placeStop(SYMBOL, QTY, new BigDecimal("90000"));
        svc.cancelStop(id);

        assertTrue(svc.triggerStop(id).isEmpty());
    }

    @Test
    void validationRejectsInvalidInputs() {
        InMemoryExchangeStopService svc = new InMemoryExchangeStopService();

        assertThrows(IllegalArgumentException.class,
                () -> svc.placeStop(null, QTY, new BigDecimal("90000")));
        assertThrows(IllegalArgumentException.class,
                () -> svc.placeStop(SYMBOL, BigDecimal.ZERO, new BigDecimal("90000")));
        assertThrows(IllegalArgumentException.class,
                () -> svc.placeStop(SYMBOL, QTY, new BigDecimal("-1")));
    }

    @Test
    void multipleStopsForDifferentSymbols() {
        InMemoryExchangeStopService svc = new InMemoryExchangeStopService();
        String id1 = svc.placeStop("KRW-BTC", QTY, new BigDecimal("90000"));
        String id2 = svc.placeStop("KRW-ETH", new BigDecimal("10"), new BigDecimal("3000"));

        assertEquals(2, svc.activeStopCount());
        svc.cancelStop(id1);
        assertEquals(1, svc.activeStopCount());
        assertEquals(0, svc.getStopPrice(id2).orElseThrow().compareTo(new BigDecimal("3000")));
    }
}
