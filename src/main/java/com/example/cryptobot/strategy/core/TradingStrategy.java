package com.example.cryptobot.strategy.core;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.risk.EntryPlan;
import com.example.cryptobot.strategy.risk.RiskManager;

import java.util.List;
import java.util.Optional;

/**
 * Pure-function strategy contract: given a candle history and the current
 * account equity, return an {@link EntryPlan} when there is a long signal,
 * or {@link Optional#empty()} when there isn't.
 *
 * <p>Strategies do not place orders themselves and do not own state. They
 * receive a {@link RiskManager} so position sizing / stop placement is
 * delegated to the central risk policy. This keeps strategies tiny and
 * trivially testable, and lets the back-test engine reuse them as-is.
 */
public interface TradingStrategy {

    /** Stable identifier used in logs / order remarks. */
    String name();

    /**
     * Evaluate the strategy on a candle window ordered oldest -> newest.
     *
     * @param candles  full window the strategy needs (caller is responsible
     *                 for passing enough bars for the indicators it uses)
     * @param equity   account equity at decision time
     * @param risk     central risk manager (provides {@link RiskManager#planLong})
     * @return entry plan if a signal fires, otherwise empty
     */
    Optional<EntryPlan> evaluate(List<Candle> candles, double equity, RiskManager risk);
}
