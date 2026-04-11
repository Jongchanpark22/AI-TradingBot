package com.example.cryptobot.strategy.range;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.core.TradingStrategy;
import com.example.cryptobot.strategy.indicator.Indicators;
import com.example.cryptobot.strategy.risk.EntryPlan;
import com.example.cryptobot.strategy.risk.RiskManager;

import java.util.List;
import java.util.Optional;

/**
 * Bollinger + RSI mean-reversion entry, intended for the {@code RANGING}
 * regime classified by {@link com.example.cryptobot.strategy.regime.RegimeClassifier}.
 *
 * <p>Why this in a ranging regime: when ADX is low, breakouts repeatedly fail
 * and the price oscillates around its mean. Buying when price is statistically
 * stretched below the mean (lower Bollinger band) and momentum is exhausted
 * (RSI &lt; 30) has positive expectancy in those conditions, especially when
 * combined with a tight ATR-based stop and a target near the band middle.
 *
 * <p>Filters:
 * <ol>
 *     <li><b>Lower band touch</b>: latest low must have pierced the lower
 *         Bollinger band — a literal "stretched below mean" event.</li>
 *     <li><b>Wilder RSI &lt; 30</b>: oversold confirmation.</li>
 *     <li><b>Bullish reversal candle</b>: the latest bar must close above its
 *         own open. Filters out the "knife still falling" failure mode.</li>
 * </ol>
 *
 * <p>Just like the trend strategy, sizing/stop/exit are delegated entirely to
 * the {@link RiskManager}; this class never makes a money decision.
 */
public final class MeanReversionStrategy implements TradingStrategy {

    private final int bbPeriod;
    private final double bbStdDev;
    private final int rsiPeriod;
    private final double rsiOversold;
    private final int atrPeriod;

    public MeanReversionStrategy() {
        this(20, 2.0, 14, 30.0, 14);
    }

    public MeanReversionStrategy(int bbPeriod, double bbStdDev, int rsiPeriod, double rsiOversold, int atrPeriod) {
        this.bbPeriod = bbPeriod;
        this.bbStdDev = bbStdDev;
        this.rsiPeriod = rsiPeriod;
        this.rsiOversold = rsiOversold;
        this.atrPeriod = atrPeriod;
    }

    @Override
    public String name() {
        return "MeanReversion(BB" + bbPeriod + "/RSI" + rsiPeriod + ")";
    }

    @Override
    public Optional<EntryPlan> evaluate(List<Candle> candles, double equity, RiskManager risk) {
        int needed = Math.max(bbPeriod + 1, Math.max(rsiPeriod + 1, 2 * atrPeriod + 1));
        if (candles == null || candles.size() < needed) return Optional.empty();

        Candle last = candles.get(candles.size() - 1);
        List<Double> closes = Indicators.closes(candles);

        // 1) Lower band pierce
        Indicators.BollingerBand bb = Indicators.bollinger(closes, bbPeriod, bbStdDev);
        if (Double.isNaN(bb.lower())) return Optional.empty();
        if (last.getLowPrice().doubleValue() > bb.lower()) return Optional.empty();

        // 2) RSI oversold
        double rsi = Indicators.rsi(closes, rsiPeriod);
        if (rsi >= rsiOversold) return Optional.empty();

        // 3) Bullish reversal candle (close > open)
        if (last.getClosePrice().compareTo(last.getOpenPrice()) <= 0) return Optional.empty();

        // 4) Risk-managed plan at the close
        double atr = Indicators.atr(candles, atrPeriod);
        EntryPlan plan = risk.planLong(equity, last.getClosePrice().doubleValue(), atr);
        return plan.isExecutable() ? Optional.of(plan) : Optional.empty();
    }
}
