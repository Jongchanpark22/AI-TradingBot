package com.example.cryptobot.strategy.regime;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.core.TradingStrategy;
import com.example.cryptobot.strategy.risk.EntryPlan;
import com.example.cryptobot.strategy.risk.RiskManager;

import java.util.List;
import java.util.Optional;

/**
 * Top-level decision maker. Classifies the current regime and forwards the
 * candle window to the strategy that fits it.
 *
 * <pre>
 *   TRENDING_UP   -> trendStrategy   (e.g. Donchian breakout)
 *   RANGING       -> rangeStrategy   (e.g. BB + RSI mean reversion)
 *   TRENDING_DOWN -> stand aside     (no shorts in this iteration)
 *   NEUTRAL       -> stand aside
 * </pre>
 *
 * <p>The router is intentionally dumb: it does not combine signals or vote.
 * That keeps it auditable — every entry has exactly one strategy attached to
 * it, which makes per-strategy PnL trivial to attribute and back-test.
 */
public final class RegimeRouter {

    private final RegimeClassifier classifier;
    private final TradingStrategy trendStrategy;
    private final TradingStrategy rangeStrategy;

    public RegimeRouter(RegimeClassifier classifier,
                        TradingStrategy trendStrategy,
                        TradingStrategy rangeStrategy) {
        this.classifier = classifier;
        this.trendStrategy = trendStrategy;
        this.rangeStrategy = rangeStrategy;
    }

    public RoutedDecision decide(List<Candle> candles, double equity, RiskManager risk) {
        MarketRegime regime = classifier.classify(candles);
        switch (regime) {
            case TRENDING_UP -> {
                Optional<EntryPlan> p = trendStrategy.evaluate(candles, equity, risk);
                return new RoutedDecision(regime, trendStrategy.name(), p);
            }
            case RANGING -> {
                Optional<EntryPlan> p = rangeStrategy.evaluate(candles, equity, risk);
                return new RoutedDecision(regime, rangeStrategy.name(), p);
            }
            case TRENDING_DOWN, NEUTRAL -> {
                return new RoutedDecision(regime, "stand-aside", Optional.empty());
            }
        }
        return new RoutedDecision(regime, "stand-aside", Optional.empty());
    }

    /** Result of a router decision. {@code plan} is empty when no entry should fire. */
    public record RoutedDecision(MarketRegime regime, String strategyName, Optional<EntryPlan> plan) {
        public boolean shouldEnter() { return plan.isPresent(); }
    }
}
