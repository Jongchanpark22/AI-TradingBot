package com.example.cryptobot.exchange.upbit.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UpbitWebSocketClient's state management.
 * These test local state only — no actual network connections.
 */
class UpbitWebSocketClientTest {

    private final UpbitWebSocketClient client = new UpbitWebSocketClient();

    @AfterEach
    void cleanup() {
        client.stop();
    }

    @Test
    void initialStateIsDisconnected() {
        assertFalse(client.isConnected());
        assertTrue(client.subscribedSymbols().isEmpty());
    }

    @Test
    void addSymbolsBeforeStart() {
        client.addSymbols(List.of("KRW-BTC", "KRW-ETH"));
        // symbols are tracked even before start
        assertTrue(client.subscribedSymbols().contains("KRW-BTC"));
        assertTrue(client.subscribedSymbols().contains("KRW-ETH"));
    }

    @Test
    void removeSymbolsReducesSet() {
        client.addSymbols(List.of("KRW-BTC", "KRW-ETH", "KRW-SOL"));
        client.removeSymbols(List.of("KRW-ETH"));
        assertEquals(Set.of("KRW-BTC", "KRW-SOL"), client.subscribedSymbols());
    }

    @Test
    void subscribedSymbolsIsUnmodifiable() {
        client.addSymbols(List.of("KRW-BTC"));
        assertThrows(UnsupportedOperationException.class,
                () -> client.subscribedSymbols().add("KRW-ETH"));
    }

    @Test
    void tickListenerCanBeRegistered() {
        List<String> received = new CopyOnWriteArrayList<>();
        client.onTick((symbol, price) -> received.add(symbol + ":" + price));
        // listener is registered but no ticks yet (no connection)
        assertTrue(received.isEmpty());
    }

    @Test
    void stopIsIdempotent() {
        client.stop(); // no-op when not started
        client.stop(); // still no-op
        assertFalse(client.isConnected());
    }
}
