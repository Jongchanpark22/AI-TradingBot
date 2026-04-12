package com.example.cryptobot.strategy.backtest;

import java.util.Collections;
import java.util.List;

/**
 * Aggregate output of a {@link BacktestEngine} run.
 *
 * <p>{@code equityCurve} is one entry per processed bar — useful for plotting
 * and for computing higher-order stats. The pre-computed scalar fields are the
 * minimum set we want surfaced when comparing parameter sweeps.
 *
 * <ul>
 *     <li>{@code totalReturn} — final equity / starting equity − 1</li>
 *     <li>{@code maxDrawdown} — worst peak-to-trough decline on the equity
 *         curve, expressed as a positive fraction (0.18 == 18% drawdown)</li>
 *     <li>{@code winRate} — closed wins / closed trades</li>
 *     <li>{@code expectancyR} — average R-multiple per trade. {@code > 0} is
 *         the headline number telling you the strategy has a positive edge.</li>
 *     <li>{@code profitFactor} — sum(wins) / |sum(losses)|; {@code > 1} means
 *         the average winning trade exceeds the average losing trade scaled by
 *         hit rate.</li>
 * </ul>
 */
public record BacktestResult(
        double startingEquity,
        double finalEquity,
        double totalReturn,
        double maxDrawdown,
        int totalTrades,
        int winningTrades,
        double winRate,
        double expectancyR,
        double profitFactor,
        List<BacktestTrade> trades,
        List<Double> equityCurve
) {
    public BacktestResult {
        trades = trades == null ? List.of() : Collections.unmodifiableList(trades);
        equityCurve = equityCurve == null ? List.of() : Collections.unmodifiableList(equityCurve);
    }

    /** Number of losing trades, derived. */
    public int losingTrades() {
        return totalTrades - winningTrades;
    }
}
