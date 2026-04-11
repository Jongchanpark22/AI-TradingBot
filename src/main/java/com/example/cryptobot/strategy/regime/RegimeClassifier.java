package com.example.cryptobot.strategy.regime;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.indicator.Indicators;

import java.util.List;

/**
 * Classifies a candle series into a {@link MarketRegime} using ADX and the
 * directional indicators.
 *
 * <p>Why ADX? It's the most widely used non-directional measure of trend
 * <em>strength</em>. The classic Wilder thresholds are:
 * <ul>
 *     <li>ADX &lt; 20 — no trend (RANGING)</li>
 *     <li>20 ≤ ADX ≤ 25 — undecided (NEUTRAL, stand aside)</li>
 *     <li>ADX &gt; 25 — trending — direction taken from +DI vs -DI</li>
 * </ul>
 * The thresholds are configurable so they can be tuned per symbol.
 */
public final class RegimeClassifier {

    private final int adxPeriod;
    private final double trendingThreshold;
    private final double rangingThreshold;

    public RegimeClassifier() {
        this(14, 25.0, 20.0);
    }

    public RegimeClassifier(int adxPeriod, double trendingThreshold, double rangingThreshold) {
        if (adxPeriod < 2) throw new IllegalArgumentException("adxPeriod must be >= 2");
        if (trendingThreshold <= rangingThreshold)
            throw new IllegalArgumentException("trendingThreshold must be > rangingThreshold");
        this.adxPeriod = adxPeriod;
        this.trendingThreshold = trendingThreshold;
        this.rangingThreshold = rangingThreshold;
    }

    public MarketRegime classify(List<Candle> candles) {
        Indicators.AdxValue v = Indicators.adx(candles, adxPeriod);
        if (!v.isValid()) return MarketRegime.NEUTRAL;

        if (v.adx() >= trendingThreshold) {
            return v.plusDi() >= v.minusDi()
                    ? MarketRegime.TRENDING_UP
                    : MarketRegime.TRENDING_DOWN;
        }
        if (v.adx() <= rangingThreshold) {
            return MarketRegime.RANGING;
        }
        return MarketRegime.NEUTRAL;
    }

    public int adxPeriod() { return adxPeriod; }
    public double trendingThreshold() { return trendingThreshold; }
    public double rangingThreshold() { return rangingThreshold; }
}
