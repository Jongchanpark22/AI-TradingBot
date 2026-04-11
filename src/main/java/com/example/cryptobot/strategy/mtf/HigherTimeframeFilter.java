package com.example.cryptobot.strategy.mtf;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.indicator.Indicators;
import com.example.cryptobot.strategy.regime.MarketRegime;
import com.example.cryptobot.strategy.regime.RegimeClassifier;

import java.util.List;

/**
 * Encapsulates "is the higher timeframe friendly to a long entry?".
 *
 * <p>Two checks, both intentionally cheap:
 * <ol>
 *     <li><b>Trend filter (daily / 1d):</b> the latest close on the higher
 *         timeframe must be above its EMA(200). Below it the bot refuses
 *         every long because we are by definition trading against the
 *         dominant trend.</li>
 *     <li><b>Strength filter (intermediate / 4h):</b> ADX on the
 *         intermediate timeframe must indicate a non-bearish regime. We
 *         allow {@link MarketRegime#TRENDING_UP}, {@link MarketRegime#RANGING}
 *         and {@link MarketRegime#NEUTRAL}; we reject only
 *         {@link MarketRegime#TRENDING_DOWN}. The router on the lower
 *         timeframe is the one that decides whether to actually fire — this
 *         filter just blocks the obvious "you're long while the bigger
 *         picture is collapsing" scenario.</li>
 * </ol>
 *
 * <p>Either filter being missing data is treated as "do not block". This
 * avoids the bot freezing when a higher-timeframe series happens to be too
 * short, e.g. on a freshly listed symbol.
 */
public final class HigherTimeframeFilter {

    private final int trendEmaPeriod;
    private final RegimeClassifier intermediateClassifier;

    public HigherTimeframeFilter() {
        this(200, new RegimeClassifier());
    }

    public HigherTimeframeFilter(int trendEmaPeriod, RegimeClassifier intermediateClassifier) {
        if (trendEmaPeriod < 2) throw new IllegalArgumentException("trendEmaPeriod must be >= 2");
        this.trendEmaPeriod = trendEmaPeriod;
        this.intermediateClassifier = intermediateClassifier;
    }

    /**
     * @param dailyCandles         daily (or otherwise "macro") candles, oldest -> newest
     * @param intermediateCandles  intermediate-timeframe candles (e.g. 4h)
     * @return a {@link Decision} explaining the verdict
     */
    public Decision check(List<Candle> dailyCandles, List<Candle> intermediateCandles) {
        // 1. daily EMA(trendEmaPeriod) trend filter
        if (dailyCandles != null && dailyCandles.size() >= trendEmaPeriod) {
            double ema = Indicators.ema(Indicators.closes(dailyCandles), trendEmaPeriod);
            double lastClose = dailyCandles.get(dailyCandles.size() - 1).getClosePrice();
            if (!Double.isNaN(ema) && lastClose < ema) {
                return Decision.block("daily close " + lastClose + " < EMA" + trendEmaPeriod + " " + ema);
            }
        }

        // 2. intermediate-timeframe regime
        if (intermediateCandles != null) {
            MarketRegime regime = intermediateClassifier.classify(intermediateCandles);
            if (regime == MarketRegime.TRENDING_DOWN) {
                return Decision.block("intermediate timeframe regime is TRENDING_DOWN");
            }
        }

        return Decision.allow();
    }

    public record Decision(boolean allowed, String reason) {
        public static Decision allow() { return new Decision(true, "ok"); }
        public static Decision block(String reason) { return new Decision(false, reason); }
    }
}
