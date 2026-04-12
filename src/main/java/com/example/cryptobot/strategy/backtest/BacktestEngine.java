package com.example.cryptobot.strategy.backtest;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.indicator.Indicators;
import com.example.cryptobot.strategy.regime.RegimeRouter;
import com.example.cryptobot.strategy.risk.EntryPlan;
import com.example.cryptobot.strategy.risk.RiskManager;
import com.example.cryptobot.strategy.risk.TrailingDecision;

import java.util.ArrayList;
import java.util.List;

/**
 * Single-symbol historical replay over a {@link RegimeRouter} +
 * {@link RiskManager} pipeline.
 *
 * <p>Design notes:
 * <ul>
 *   <li><b>Pure-function</b>: takes a candle list and configuration, returns a
 *       {@link BacktestResult}. No I/O, no globals — safe to call from a
 *       parameter sweep.</li>
 *   <li><b>One position at a time</b>: keeps the test loop honest and means
 *       per-trade R-multiples are directly comparable. Multi-symbol portfolio
 *       backtests are a separate concern and would compose multiple engines.</li>
 *   <li><b>Bar-granularity fills</b>: entry fills at the next bar's open
 *       (avoids look-ahead). Stops/targets are checked against intra-bar
 *       high/low, with the conservative tie-breaker that on a bar that touches
 *       both, the stop fires first. That's the standard back-test convention
 *       and biases the result <em>against</em> the strategy, which is what you
 *       want.</li>
 *   <li><b>Trailing</b> uses the production {@link RiskManager#updateTrailing}
 *       so the back-test cannot diverge from live behaviour.</li>
 * </ul>
 *
 * <p>Intentionally not modelled: slippage, fees, partial fills, multi-leg
 * orders. Those are second-order corrections — the first job of the back-test
 * is to answer "does this strategy have a positive edge at all?" and adding
 * realism on top of a strategy that loses money in the idealised case is
 * wasted effort.
 */
public final class BacktestEngine {

    private final RegimeRouter router;
    private final RiskManager risk;
    private final int atrPeriod;
    private final int warmupBars;

    public BacktestEngine(RegimeRouter router, RiskManager risk, int atrPeriod, int warmupBars) {
        if (router == null) throw new IllegalArgumentException("router required");
        if (risk == null) throw new IllegalArgumentException("risk required");
        if (atrPeriod < 2) throw new IllegalArgumentException("atrPeriod must be >= 2");
        if (warmupBars < atrPeriod) throw new IllegalArgumentException("warmupBars must be >= atrPeriod");
        this.router = router;
        this.risk = risk;
        this.atrPeriod = atrPeriod;
        this.warmupBars = warmupBars;
    }

    public BacktestResult run(String symbol, List<Candle> candles, double startingEquity) {
        if (candles == null || candles.size() <= warmupBars + 1) {
            return emptyResult(startingEquity);
        }

        double equity = startingEquity;
        double peakEquity = startingEquity;
        double maxDrawdown = 0;

        List<Double> equityCurve = new ArrayList<>(candles.size() - warmupBars);
        List<BacktestTrade> trades = new ArrayList<>();

        OpenPosition pos = null;

        for (int i = warmupBars; i < candles.size(); i++) {
            // Window visible to the strategy at decision time: bars [0..i].
            List<Candle> window = candles.subList(0, i + 1);
            Candle bar = candles.get(i);

            if (pos != null) {
                // ----- manage existing position on this bar -------------
                double high = bar.getHighPrice().doubleValue();
                double low = bar.getLowPrice().doubleValue();
                double close = bar.getClosePrice().doubleValue();
                double atr = Indicators.atr(window, atrPeriod);

                pos.highestSeen = Math.max(pos.highestSeen, high);

                // Conservative bar-walk: stop first, then partial/trail.
                if (low <= pos.currentStop) {
                    equity = closePosition(pos, pos.currentStop, bar, "stop-loss hit",
                            equity, trades, symbol);
                    pos = null;
                } else {
                    TrailingDecision d = risk.updateTrailing(
                            pos.entryPrice, pos.initialStop, pos.currentStop,
                            close, pos.highestSeen, atr, pos.partialDone);

                    if (d.shouldExitNow()) {
                        equity = closePosition(pos, close, bar, d.reason(),
                                equity, trades, symbol);
                        pos = null;
                    } else {
                        if (d.shouldPartialExit()) {
                            // Realise half at the current close.
                            double realised = (close - pos.entryPrice) * (pos.quantity / 2.0);
                            equity += realised;
                            pos.quantity = pos.quantity / 2.0;
                            pos.partialDone = true;
                        }
                        if (d.newStopLoss() > pos.currentStop) {
                            pos.currentStop = d.newStopLoss();
                        }
                    }
                }
            }

            if (pos == null && i + 1 < candles.size()) {
                // ----- look for entry; fill at next bar open ------------
                RegimeRouter.RoutedDecision rd = router.decide(window, equity, risk);
                if (rd.shouldEnter()) {
                    EntryPlan plan = rd.plan().get();
                    Candle nextBar = candles.get(i + 1);
                    double fill = nextBar.getOpenPrice().doubleValue();
                    pos = new OpenPosition(rd.strategyName(), nextBar.getTimestamp(),
                            fill, plan.quantity(), plan.stopLossPrice(),
                            plan.takeProfitPrice(), plan.atrAtEntry());
                }
            }

            // Mark-to-market for the equity curve.
            double mtmEquity = equity;
            if (pos != null) {
                mtmEquity += (bar.getClosePrice().doubleValue() - pos.entryPrice) * pos.quantity;
            }
            peakEquity = Math.max(peakEquity, mtmEquity);
            double dd = peakEquity == 0 ? 0 : (peakEquity - mtmEquity) / peakEquity;
            if (dd > maxDrawdown) maxDrawdown = dd;
            equityCurve.add(mtmEquity);
        }

        // Force-close anything still open at the last bar's close, otherwise
        // an unrealised winning trade would silently not contribute to the
        // win-rate / expectancy stats.
        if (pos != null) {
            Candle lastBar = candles.get(candles.size() - 1);
            equity = closePosition(pos, lastBar.getClosePrice().doubleValue(),
                    lastBar, "end of data", equity, trades, symbol);
        }

        return summarize(startingEquity, equity, maxDrawdown, trades, equityCurve);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static double closePosition(
            OpenPosition pos, double exitPrice, Candle exitBar, String reason,
            double equity, List<BacktestTrade> trades, String symbol) {
        double pnl = (exitPrice - pos.entryPrice) * pos.quantity;
        double initialRisk = pos.entryPrice - pos.initialStop;
        double r = initialRisk > 0 ? (exitPrice - pos.entryPrice) / initialRisk : 0;
        trades.add(new BacktestTrade(
                symbol, pos.strategyName, pos.entryTime, pos.entryPrice, pos.quantity,
                pos.initialStop, exitBar.getTimestamp(), exitPrice, reason, pnl, r));
        return equity + pnl;
    }

    private static BacktestResult summarize(
            double startingEquity, double finalEquity, double maxDrawdown,
            List<BacktestTrade> trades, List<Double> equityCurve) {
        int total = trades.size();
        int wins = 0;
        double sumR = 0;
        double sumWin = 0;
        double sumLossAbs = 0;
        for (BacktestTrade t : trades) {
            sumR += t.rMultiple();
            if (t.isWin()) {
                wins++;
                sumWin += t.pnl();
            } else {
                sumLossAbs += -t.pnl();
            }
        }
        double winRate = total == 0 ? 0 : (double) wins / total;
        double expectancyR = total == 0 ? 0 : sumR / total;
        double profitFactor = sumLossAbs == 0
                ? (sumWin > 0 ? Double.POSITIVE_INFINITY : 0)
                : sumWin / sumLossAbs;
        double totalReturn = startingEquity == 0 ? 0 : (finalEquity / startingEquity) - 1.0;

        return new BacktestResult(startingEquity, finalEquity, totalReturn, maxDrawdown,
                total, wins, winRate, expectancyR, profitFactor, trades, equityCurve);
    }

    private static BacktestResult emptyResult(double startingEquity) {
        return new BacktestResult(startingEquity, startingEquity, 0, 0, 0, 0, 0, 0, 0,
                List.of(), List.of());
    }

    /** Internal mutable position state. Not exposed; backtest is single-threaded. */
    private static final class OpenPosition {
        final String strategyName;
        final java.time.LocalDateTime entryTime;
        final double entryPrice;
        double quantity;
        final double initialStop;
        double currentStop;
        final double takeProfit;
        final double atrAtEntry;
        double highestSeen;
        boolean partialDone;

        OpenPosition(String strategyName, java.time.LocalDateTime entryTime,
                     double entryPrice, double quantity, double initialStop,
                     double takeProfit, double atrAtEntry) {
            this.strategyName = strategyName;
            this.entryTime = entryTime;
            this.entryPrice = entryPrice;
            this.quantity = quantity;
            this.initialStop = initialStop;
            this.currentStop = initialStop;
            this.takeProfit = takeProfit;
            this.atrAtEntry = atrAtEntry;
            this.highestSeen = entryPrice;
            this.partialDone = false;
        }
    }
}
