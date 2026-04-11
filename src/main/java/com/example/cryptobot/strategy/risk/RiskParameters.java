package com.example.cryptobot.strategy.risk;

/**
 * Configuration for the {@link RiskManager}.
 *
 * <p>Defaults are picked to be conservative on crypto:
 * <ul>
 *     <li>{@code riskPerTrade = 0.01} — 1% of equity at risk per trade.</li>
 *     <li>{@code stopAtrMultiplier = 1.5} — initial stop = entry − 1.5·ATR.</li>
 *     <li>{@code takeProfitRMultiple = 2.0} — final TP at 2R (i.e. risk·2 in profit).</li>
 *     <li>{@code partialExitRMultiple = 1.0} — close half at 1R, move stop to break-even.</li>
 *     <li>{@code trailingAtrMultiplier = 3.0} — Chandelier trail = highestHigh − 3·ATR.</li>
 *     <li>{@code maxDailyLoss = 0.05} — kill switch at -5% equity per day.</li>
 *     <li>{@code maxOpenPositions = 3}</li>
 * </ul>
 */
public record RiskParameters(
        double riskPerTrade,
        double stopAtrMultiplier,
        double takeProfitRMultiple,
        double partialExitRMultiple,
        double trailingAtrMultiplier,
        double maxDailyLoss,
        int maxOpenPositions
) {
    public static RiskParameters defaults() {
        return new RiskParameters(0.01, 1.5, 2.0, 1.0, 3.0, 0.05, 3);
    }

    /** Validate inputs. Throws {@link IllegalArgumentException} on bad config. */
    public RiskParameters {
        if (riskPerTrade <= 0 || riskPerTrade > 0.1)
            throw new IllegalArgumentException("riskPerTrade must be in (0, 0.1], was " + riskPerTrade);
        if (stopAtrMultiplier <= 0)
            throw new IllegalArgumentException("stopAtrMultiplier must be > 0");
        if (takeProfitRMultiple <= 0)
            throw new IllegalArgumentException("takeProfitRMultiple must be > 0");
        if (partialExitRMultiple < 0)
            throw new IllegalArgumentException("partialExitRMultiple must be >= 0");
        if (trailingAtrMultiplier <= 0)
            throw new IllegalArgumentException("trailingAtrMultiplier must be > 0");
        if (maxDailyLoss <= 0 || maxDailyLoss > 0.5)
            throw new IllegalArgumentException("maxDailyLoss must be in (0, 0.5]");
        if (maxOpenPositions <= 0)
            throw new IllegalArgumentException("maxOpenPositions must be > 0");
    }
}
