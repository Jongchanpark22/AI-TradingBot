package com.example.cryptobot.strategy.risk;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link ExchangeStopService} for testing and
 * paper-trading.
 *
 * <p>Stop orders live in a simple map keyed by their id. There is no
 * price-matching engine — the caller (test or paper broker tick loop) is
 * responsible for checking whether the market price has crossed the stop and
 * calling {@link #triggerStop} to simulate the fill.
 */
public final class InMemoryExchangeStopService implements ExchangeStopService {

    public enum StopState { ACTIVE, CANCELLED, TRIGGERED }

    public record StopOrder(String id, String symbol, BigDecimal quantity,
                            BigDecimal stopPrice, StopState state) {}

    private final Map<String, StopOrder> stops = new ConcurrentHashMap<>();

    @Override
    public String placeStop(String symbol, BigDecimal quantity, BigDecimal stopPrice) {
        validate(symbol, quantity, stopPrice);
        String id = "STOP-" + UUID.randomUUID();
        stops.put(id, new StopOrder(id, symbol, quantity, stopPrice, StopState.ACTIVE));
        return id;
    }

    @Override
    public String moveStop(String existingStopId, String symbol,
                           BigDecimal quantity, BigDecimal newStopPrice) {
        validate(symbol, quantity, newStopPrice);
        StopOrder old = stops.get(existingStopId);
        if (old == null) {
            throw new IllegalStateException("stop not found: " + existingStopId);
        }
        if (old.state() != StopState.ACTIVE) {
            throw new IllegalStateException(
                    "cannot move stop in state " + old.state() + ": " + existingStopId);
        }
        // cancel old, place new
        stops.put(existingStopId,
                new StopOrder(old.id(), old.symbol(), old.quantity(), old.stopPrice(), StopState.CANCELLED));
        String newId = "STOP-" + UUID.randomUUID();
        stops.put(newId, new StopOrder(newId, symbol, quantity, newStopPrice, StopState.ACTIVE));
        return newId;
    }

    @Override
    public boolean cancelStop(String stopId) {
        StopOrder order = stops.get(stopId);
        if (order == null) return true; // idempotent
        if (order.state() != StopState.ACTIVE) return true;
        stops.put(stopId,
                new StopOrder(order.id(), order.symbol(), order.quantity(), order.stopPrice(), StopState.CANCELLED));
        return true;
    }

    @Override
    public Optional<BigDecimal> getStopPrice(String stopId) {
        StopOrder order = stops.get(stopId);
        if (order == null || order.state() != StopState.ACTIVE) return Optional.empty();
        return Optional.of(order.stopPrice());
    }

    // ---- test helpers ----

    /**
     * Simulate the exchange triggering the stop (price crossed).
     * Returns the triggered order, or empty if it wasn't active.
     */
    public Optional<StopOrder> triggerStop(String stopId) {
        StopOrder order = stops.get(stopId);
        if (order == null || order.state() != StopState.ACTIVE) return Optional.empty();
        StopOrder triggered = new StopOrder(
                order.id(), order.symbol(), order.quantity(), order.stopPrice(), StopState.TRIGGERED);
        stops.put(stopId, triggered);
        return Optional.of(triggered);
    }

    /** Look up a stop order by id (for assertions). */
    public Optional<StopOrder> findStop(String stopId) {
        return Optional.ofNullable(stops.get(stopId));
    }

    /** Count of currently active stops. */
    public long activeStopCount() {
        return stops.values().stream().filter(s -> s.state() == StopState.ACTIVE).count();
    }

    private static void validate(String symbol, BigDecimal quantity, BigDecimal stopPrice) {
        if (symbol == null || symbol.isBlank())
            throw new IllegalArgumentException("symbol required");
        if (quantity == null || quantity.signum() <= 0)
            throw new IllegalArgumentException("quantity must be > 0");
        if (stopPrice == null || stopPrice.signum() <= 0)
            throw new IllegalArgumentException("stopPrice must be > 0");
    }
}
