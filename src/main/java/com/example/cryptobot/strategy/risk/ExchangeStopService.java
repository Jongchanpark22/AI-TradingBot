package com.example.cryptobot.strategy.risk;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Abstraction for server-side (exchange-resident) stop-loss orders.
 *
 * <p><b>Why this matters:</b> the bot's trailing stop logic runs in the JVM
 * process. If the process crashes, loses its network connection, or the host
 * machine reboots, no local code is executing — and an open position has
 * <em>no</em> stop protection at all. The damage in a flash-crash scenario is
 * unbounded.
 *
 * <p>Server-side stop hardening solves this by placing a real stop-loss order
 * on the exchange itself as a backup. The exchange will execute the stop even
 * if the bot is completely offline. As the bot's trailing logic tightens the
 * stop, it cancels the old exchange stop and places a new one at the tighter
 * price — so the exchange-side stop always mirrors the latest local stop
 * within one tick lag.
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>{@link #placeStop} places (or replaces) a stop-loss sell order on the
 *       exchange. Returns an opaque stop-order id.</li>
 *   <li>{@link #moveStop} atomically cancels the old stop and places a new
 *       one at a tighter (higher) price. Returns the new id.</li>
 *   <li>{@link #cancelStop} cancels an outstanding stop. Idempotent — calling
 *       it on an already-cancelled or filled stop returns {@code true}.</li>
 *   <li>{@link #getStopPrice} queries the current stop price on the exchange,
 *       useful for consistency checks after reconnect.</li>
 * </ul>
 *
 * <p>Implementations:
 * <ul>
 *   <li><b>Production:</b> calls the Upbit (or Binance) REST API to place a
 *       real stop-limit or stop-market order.</li>
 *   <li><b>Paper trading:</b> delegates to {@link com.example.cryptobot.strategy.paper.PaperBroker}
 *       so the same lifecycle code runs without real money.</li>
 *   <li><b>Tests:</b> uses the provided {@link InMemoryExchangeStopService}
 *       to verify stop lifecycle logic in isolation.</li>
 * </ul>
 */
public interface ExchangeStopService {

    /**
     * Place a new stop-loss sell order on the exchange.
     *
     * @param symbol    trading pair (e.g. "KRW-BTC")
     * @param quantity  amount to sell when the stop triggers
     * @param stopPrice price at which the stop triggers
     * @return opaque exchange-assigned stop order id
     */
    String placeStop(String symbol, BigDecimal quantity, BigDecimal stopPrice);

    /**
     * Move an existing stop to a new (tighter) price. Implementations should
     * cancel-then-place atomically where the exchange supports it, or
     * cancel-then-place sequentially otherwise.
     *
     * @param existingStopId  id returned by a previous {@link #placeStop} or
     *                        {@link #moveStop}
     * @param symbol          trading pair
     * @param quantity        (may differ if a partial exit reduced the position)
     * @param newStopPrice    the new, tighter stop price
     * @return new stop order id (may be the same as the old one on exchanges
     *         that support in-place amendment)
     * @throws IllegalStateException if the existing stop is already filled or
     *         cannot be found
     */
    String moveStop(String existingStopId, String symbol,
                    BigDecimal quantity, BigDecimal newStopPrice);

    /**
     * Cancel an outstanding stop order. Idempotent: returns {@code true} if the
     * stop was cancelled or was already gone (filled / expired / not found).
     */
    boolean cancelStop(String stopId);

    /**
     * Query the current stop price for a given stop order id.
     *
     * @return the stop price, or empty if the order is no longer active
     */
    Optional<BigDecimal> getStopPrice(String stopId);
}
