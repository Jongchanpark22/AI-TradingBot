package com.example.cryptobot.strategy.regime;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.range.MeanReversionStrategy;
import com.example.cryptobot.strategy.risk.RiskManager;
import com.example.cryptobot.strategy.risk.RiskParameters;
import com.example.cryptobot.strategy.trend.DonchianBreakoutStrategy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RegimeRouterTest {

    private final RiskManager risk = new RiskManager(RiskParameters.defaults());
    private final RegimeClassifier classifier = new RegimeClassifier();
    private final DonchianBreakoutStrategy trend = new DonchianBreakoutStrategy();
    private final MeanReversionStrategy range = new MeanReversionStrategy();
    private final RegimeRouter router = new RegimeRouter(classifier, trend, range);

    // ----- helpers --------------------------------------------------------

    private static Candle bar(double o, double h, double l, double c, double v) {
        return Candle.builder()
                .symbol("TEST")
                .period(Candle.CandlePeriod.ONE_HOUR)
                .timestamp(LocalDateTime.now())
                .openPrice(java.math.BigDecimal.valueOf(o))
                .highPrice(java.math.BigDecimal.valueOf(h))
                .lowPrice(java.math.BigDecimal.valueOf(l))
                .closePrice(java.math.BigDecimal.valueOf(c))
                .volume(java.math.BigDecimal.valueOf(v))
                .quoteAssetVolume(java.math.BigDecimal.valueOf(v * c))
                .build();
    }

    /** Strong, steady uptrend with rising volume on the breakout bar. */
    private static List<Candle> strongUptrend(int n) {
        List<Candle> out = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < n; i++) {
            double open = price;
            double close = price + 1.5;
            double high = close + 0.4;
            double low = open - 0.2;
            // last bar gets a volume spike to satisfy DonchianBreakoutStrategy's filter
            double vol = (i == n - 1) ? 5000 : 1000;
            out.add(bar(open, high, low, close, vol));
            price = close;
        }
        return out;
    }

    /** Steady downtrend so ADX is high but +DI < -DI. */
    private static List<Candle> strongDowntrend(int n) {
        List<Candle> out = new ArrayList<>();
        double price = 200.0;
        for (int i = 0; i < n; i++) {
            double open = price;
            double close = price - 1.5;
            double high = open + 0.2;
            double low = close - 0.4;
            out.add(bar(open, high, low, close, 1000));
            price = close;
        }
        return out;
    }

    /** Tight oscillation — ADX should stay below the ranging threshold. */
    private static List<Candle> tightRange(int n, long seed) {
        Random r = new Random(seed);
        List<Candle> out = new ArrayList<>();
        double mid = 100.0;
        for (int i = 0; i < n; i++) {
            double noise = (r.nextDouble() - 0.5) * 0.6;
            double open = mid + noise;
            double close = mid - noise;
            double high = Math.max(open, close) + 0.4;
            double low = Math.min(open, close) - 0.4;
            out.add(bar(open, high, low, close, 1000));
        }
        return out;
    }

    /** Test double: a strategy that always fires, used to isolate routing logic. */
    private static class AlwaysFiresStrategy implements com.example.cryptobot.strategy.core.TradingStrategy {
        private final String name;
        AlwaysFiresStrategy(String name) { this.name = name; }
        @Override public String name() { return name; }
        @Override
        public java.util.Optional<com.example.cryptobot.strategy.risk.EntryPlan> evaluate(
                List<Candle> candles, double equity,
                com.example.cryptobot.strategy.risk.RiskManager risk) {
            return java.util.Optional.of(
                    new com.example.cryptobot.strategy.risk.EntryPlan(
                            1, 100, 99, 102, 1, 2, 1));
        }
    }

    // ----- classifier -----------------------------------------------------

    @Test
    void classifierFlagsTrendingUp() {
        assertEquals(MarketRegime.TRENDING_UP, classifier.classify(strongUptrend(80)));
    }

    @Test
    void classifierFlagsTrendingDown() {
        assertEquals(MarketRegime.TRENDING_DOWN, classifier.classify(strongDowntrend(80)));
    }

    @Test
    void classifierFlagsRanging() {
        assertEquals(MarketRegime.RANGING, classifier.classify(tightRange(80, 1)));
    }

    @Test
    void classifierReturnsNeutralWhenNotEnoughBars() {
        assertEquals(MarketRegime.NEUTRAL, classifier.classify(strongUptrend(5)));
    }

    // ----- router ---------------------------------------------------------

    @Test
    void routerEntersOnTrendingUpViaDonchian() {
        RegimeRouter.RoutedDecision d = router.decide(strongUptrend(80), 10_000, risk);
        assertEquals(MarketRegime.TRENDING_UP, d.regime());
        assertTrue(d.shouldEnter(), "Donchian breakout should fire on a strong uptrend with volume");
        assertTrue(d.strategyName().startsWith("DonchianBreakout"));
    }

    @Test
    void routerStandsAsideOnTrendingDown() {
        RegimeRouter.RoutedDecision d = router.decide(strongDowntrend(80), 10_000, risk);
        assertEquals(MarketRegime.TRENDING_DOWN, d.regime());
        assertFalse(d.shouldEnter(), "no shorts in this iteration");
    }

    @Test
    void routerStandsAsideOnNoisySidewaysWithoutSetup() {
        RegimeRouter.RoutedDecision d = router.decide(tightRange(80, 13), 10_000, risk);
        assertEquals(MarketRegime.RANGING, d.regime());
        assertFalse(d.shouldEnter(), "no oversold bar -> mean-reversion stays out");
    }

    @Test
    void routerDelegatesToRangeStrategyOnRangingRegime() {
        // Use a stub range strategy so this test isolates ROUTING from
        // the real mean-reversion entry math (covered separately in
        // MeanReversionStrategyTest). Building data that simultaneously
        // satisfies both ADX-low and RSI-oversold is impossible because
        // the bars needed to drag RSI below 30 also push ADX into the
        // trending zone — so we test the two concerns independently.
        AlwaysFiresStrategy stubRange = new AlwaysFiresStrategy("StubRange");
        RegimeRouter customRouter = new RegimeRouter(classifier, trend, stubRange);
        RegimeRouter.RoutedDecision d = customRouter.decide(tightRange(80, 13), 10_000, risk);
        assertEquals(MarketRegime.RANGING, d.regime());
        assertTrue(d.shouldEnter(), "router should hand off to the range strategy");
        assertEquals("StubRange", d.strategyName());
    }

    @Test
    void volumeFilterRejectsLowLiquidityBreakout() {
        // remove the volume spike on the breakout bar -> Donchian should refuse
        List<Candle> c = strongUptrend(80);
        Candle last = c.get(c.size() - 1);
        c.set(c.size() - 1,
                Candle.builder()
                        .symbol(last.getSymbol()).period(last.getPeriod())
                        .timestamp(last.getTimestamp())
                        .openPrice(last.getOpenPrice()).highPrice(last.getHighPrice())
                        .lowPrice(last.getLowPrice()).closePrice(last.getClosePrice())
                        .volume(java.math.BigDecimal.valueOf(900.0)).quoteAssetVolume(last.getQuoteAssetVolume())
                        .build());
        RegimeRouter.RoutedDecision d = router.decide(c, 10_000, risk);
        assertEquals(MarketRegime.TRENDING_UP, d.regime());
        assertFalse(d.shouldEnter(), "low-liquidity breakout must be rejected");
    }
}
