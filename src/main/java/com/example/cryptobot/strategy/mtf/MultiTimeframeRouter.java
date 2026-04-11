package com.example.cryptobot.strategy.mtf;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.regime.RegimeRouter;
import com.example.cryptobot.strategy.risk.RiskManager;

import java.util.List;
import java.util.Optional;

/**
 * Composes a per-timeframe {@link RegimeRouter} with a
 * {@link HigherTimeframeFilter} so the entry decision is taken on the
 * lower timeframe but only when the higher timeframes agree.
 *
 * <p>This is the "top-down analysis" professional traders use:
 * <pre>
 *     daily      -> "are we in an uptrend at all?"   (EMA200 filter)
 *     4h         -> "is momentum on our side?"       (ADX regime)
 *     1h         -> "where exactly do I enter?"      (RegimeRouter)
 * </pre>
 * Skipping it is the most common reason naive bots take losing trades that
 * look great on the entry timeframe but fight a much bigger move on the
 * higher one.
 *
 * <p>{@link Decision#blocked()} carries the reason so callers / logs can
 * attribute every skipped trade to the exact filter that vetoed it.
 */
public final class MultiTimeframeRouter {

    private final HigherTimeframeFilter htfFilter;
    private final RegimeRouter ltfRouter;

    public MultiTimeframeRouter(HigherTimeframeFilter htfFilter, RegimeRouter ltfRouter) {
        this.htfFilter = htfFilter;
        this.ltfRouter = ltfRouter;
    }

    public Decision decide(
            List<Candle> dailyCandles,
            List<Candle> intermediateCandles,
            List<Candle> entryCandles,
            double equity,
            RiskManager risk) {

        HigherTimeframeFilter.Decision htf = htfFilter.check(dailyCandles, intermediateCandles);
        if (!htf.allowed()) {
            return Decision.blocked(htf.reason());
        }
        RegimeRouter.RoutedDecision routed = ltfRouter.decide(entryCandles, equity, risk);
        return Decision.fromRouted(routed);
    }

    public record Decision(
            boolean shouldEnter,
            String reason,
            Optional<RegimeRouter.RoutedDecision> routed
    ) {
        public static Decision blocked(String reason) {
            return new Decision(false, "HTF blocked: " + reason, Optional.empty());
        }
        public static Decision fromRouted(RegimeRouter.RoutedDecision r) {
            return new Decision(r.shouldEnter(),
                    r.shouldEnter() ? r.strategyName() + " on " + r.regime() : "no LTF signal",
                    Optional.of(r));
        }
    }
}
