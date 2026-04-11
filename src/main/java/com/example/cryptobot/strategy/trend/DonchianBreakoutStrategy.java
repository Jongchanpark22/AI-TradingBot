package com.example.cryptobot.strategy.trend;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.core.TradingStrategy;
import com.example.cryptobot.strategy.indicator.Indicators;
import com.example.cryptobot.strategy.risk.EntryPlan;
import com.example.cryptobot.strategy.risk.RiskManager;

import java.util.List;
import java.util.Optional;

/**
 * Donchian breakout entry, the spiritual descendant of the Turtle system.
 *
 * <p>Why this in a trending regime: it is the simplest, most-validated trend
 * entry that survives across decades of futures and crypto data. It buys
 * strength: by definition the bar that triggers an entry has just made a new
 * {@code N}-bar high, so the strategy never tries to "catch a bottom" — it
 * waits for the market to confirm a leg up before risking capital.
 *
 * <p>Filters layered on top of the raw breakout (each one rejects a known
 * failure mode):
 * <ol>
 *     <li><b>Donchian-excluding-last</b>: high is computed over the prior
 *         {@code period} bars, not the current bar — otherwise every bar
 *         trivially equals its own high.</li>
 *     <li><b>Supertrend filter</b>: trades only when Supertrend is up. Cuts
 *         the typical "first breakout fails inside an exhausted move" loss.</li>
 *     <li><b>Volume filter</b>: current volume must be at least
 *         {@code minVolumeRatio * 20-bar volume average}. Filters out the
 *         classic low-liquidity fakeout that wrecks naive breakout bots in
 *         crypto.</li>
 * </ol>
 *
 * <p>Position sizing, stop placement, and trailing are entirely delegated to
 * the {@link RiskManager} (Phase 2). This strategy never makes a sizing or
 * exit decision on its own.
 */
public final class DonchianBreakoutStrategy implements TradingStrategy {

    private final int donchianPeriod;
    private final int atrPeriod;
    private final int supertrendPeriod;
    private final double supertrendMultiplier;
    private final int volumeAvgPeriod;
    private final double minVolumeRatio;

    public DonchianBreakoutStrategy() {
        this(20, 14, 10, 3.0, 20, 1.3);
    }

    public DonchianBreakoutStrategy(
            int donchianPeriod,
            int atrPeriod,
            int supertrendPeriod,
            double supertrendMultiplier,
            int volumeAvgPeriod,
            double minVolumeRatio) {
        this.donchianPeriod = donchianPeriod;
        this.atrPeriod = atrPeriod;
        this.supertrendPeriod = supertrendPeriod;
        this.supertrendMultiplier = supertrendMultiplier;
        this.volumeAvgPeriod = volumeAvgPeriod;
        this.minVolumeRatio = minVolumeRatio;
    }

    @Override
    public String name() {
        return "DonchianBreakout(" + donchianPeriod + ")";
    }

    @Override
    public Optional<EntryPlan> evaluate(List<Candle> candles, double equity, RiskManager risk) {
        int needed = Math.max(donchianPeriod + 1,
                Math.max(2 * atrPeriod + 1, supertrendPeriod + 2));
        if (candles == null || candles.size() < needed) return Optional.empty();

        Candle last = candles.get(candles.size() - 1);
        double close = last.getClosePrice().doubleValue();

        // 1) Donchian breakout (excluding current bar)
        Indicators.DonchianChannel d = Indicators.donchianExcludingLast(candles, donchianPeriod);
        if (Double.isNaN(d.upper()) || close <= d.upper()) return Optional.empty();

        // 2) Supertrend in agreement
        Indicators.SupertrendPoint st = Indicators.supertrend(candles, supertrendPeriod, supertrendMultiplier);
        if (st == null || !st.isUp()) return Optional.empty();

        // 3) Volume confirmation
        List<Double> vols = Indicators.volumes(candles);
        double volAvg = Indicators.sma(vols, volumeAvgPeriod);
        if (Double.isNaN(volAvg) || volAvg <= 0) return Optional.empty();
        if (last.getVolume().doubleValue() < volAvg * minVolumeRatio) return Optional.empty();

        // 4) Hand off to risk manager
        double atr = Indicators.atr(candles, atrPeriod);
        EntryPlan plan = risk.planLong(equity, close, atr);
        return plan.isExecutable() ? Optional.of(plan) : Optional.empty();
    }
}
