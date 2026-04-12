package com.example.cryptobot.strategy.backtest;

import java.time.LocalDateTime;

/**
 * One closed trade in a back-test run.
 *
 * <p>Records both the entry and the exit so the analyzer can compute holding
 * period, R multiple, and per-strategy attribution. {@code rMultiple} is
 * {@code (exitPrice - entryPrice) / (entryPrice - initialStop)}, i.e. how many
 * "initial-risk units" the trade returned — this is the unit traders use to
 * compare wins and losses on the same scale, regardless of the absolute price
 * the asset happened to be trading at.
 */
public record BacktestTrade(
        String symbol,
        String strategyName,
        LocalDateTime entryTime,
        double entryPrice,
        double quantity,
        double initialStop,
        LocalDateTime exitTime,
        double exitPrice,
        String exitReason,
        double pnl,
        double rMultiple
) {
    public boolean isWin() {
        return pnl > 0;
    }
}
