package com.example.cryptobot.strategy.mtf;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.core.TradingStrategy;
import com.example.cryptobot.strategy.range.MeanReversionStrategy;
import com.example.cryptobot.strategy.regime.RegimeClassifier;
import com.example.cryptobot.strategy.regime.RegimeRouter;
import com.example.cryptobot.strategy.risk.EntryPlan;
import com.example.cryptobot.strategy.risk.RiskManager;
import com.example.cryptobot.strategy.risk.RiskParameters;
import com.example.cryptobot.strategy.trend.DonchianBreakoutStrategy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MultiTimeframeRouterTest {

    private final RiskManager risk = new RiskManager(RiskParameters.defaults());
    private final RegimeRouter ltfRouter = new RegimeRouter(
            new RegimeClassifier(),
            new DonchianBreakoutStrategy(),
            new MeanReversionStrategy());
    private final HigherTimeframeFilter htf = new HigherTimeframeFilter();
    private final MultiTimeframeRouter mtf = new MultiTimeframeRouter(htf, ltfRouter);

    // ----- helpers --------------------------------------------------------

    private static Candle bar(double o, double h, double l, double c, double v) {
        return Candle.builder()
                .symbol("TEST").period(Candle.CandlePeriod.ONE_HOUR)
                .timestamp(LocalDateTime.now())
                .openPrice(o).highPrice(h).lowPrice(l).closePrice(c)
                .volume(v).quoteAssetVolume(v * c)
                .build();
    }

    private static List<Candle> uptrend(int n, double startPrice) {
        List<Candle> out = new ArrayList<>();
        double price = startPrice;
        for (int i = 0; i < n; i++) {
            double open = price;
            double close = price + 1.5;
            double high = close + 0.4;
            double low = open - 0.2;
            double vol = (i == n - 1) ? 5000 : 1000;
            out.add(bar(open, high, low, close, vol));
            price = close;
        }
        return out;
    }

    private static List<Candle> downtrend(int n, double startPrice) {
        List<Candle> out = new ArrayList<>();
        double price = startPrice;
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

    /** Stub strategy that always returns a plan. */
    private static class AlwaysFires implements TradingStrategy {
        @Override public String name() { return "Stub"; }
        @Override public Optional<EntryPlan> evaluate(List<Candle> c, double e, RiskManager r) {
            return Optional.of(new EntryPlan(1, 100, 99, 102, 1, 2, 1));
        }
    }

    // ----- HigherTimeframeFilter -----------------------------------------

    @Test
    void htfBlocksWhenDailyIsBelowEma200() {
        // 250 bars of downtrend so the daily close ends well below its own EMA(200)
        List<Candle> daily = downtrend(250, 1000);
        HigherTimeframeFilter.Decision d = htf.check(daily, uptrend(80, 100));
        assertFalse(d.allowed(), "daily below EMA200 must block");
        assertTrue(d.reason().contains("EMA200"));
    }

    @Test
    void htfBlocksWhenIntermediateIsTrendingDown() {
        // daily flat-ish (close == start price, so above any short EMA)
        // intermediate strongly down -> classifier says TRENDING_DOWN
        List<Candle> daily = uptrend(250, 100);
        List<Candle> intermediate = downtrend(80, 1000);
        HigherTimeframeFilter.Decision d = htf.check(daily, intermediate);
        assertFalse(d.allowed());
        assertTrue(d.reason().contains("TRENDING_DOWN"));
    }

    @Test
    void htfAllowsWhenDailyUpAndIntermediateNotBearish() {
        HigherTimeframeFilter.Decision d = htf.check(uptrend(250, 100), uptrend(80, 100));
        assertTrue(d.allowed());
    }

    @Test
    void htfDoesNotBlockOnInsufficientDailyHistory() {
        // freshly listed symbol: only 50 daily bars, EMA200 cannot be computed
        HigherTimeframeFilter.Decision d = htf.check(uptrend(50, 100), uptrend(80, 100));
        assertTrue(d.allowed(), "insufficient HTF data should not block");
    }

    // ----- MultiTimeframeRouter ------------------------------------------

    @Test
    void mtfDelegatesToLtfWhenHtfAllows() {
        MultiTimeframeRouter.Decision d = mtf.decide(
                uptrend(250, 100),
                uptrend(80, 100),
                uptrend(80, 100),
                10_000, risk);
        assertTrue(d.shouldEnter());
        assertTrue(d.routed().isPresent());
    }

    @Test
    void mtfBlocksDespiteValidLtfSignalWhenHtfVetoesIt() {
        // LTF would fire (Donchian on uptrend with volume) but daily is in
        // a downtrend below its own EMA200 -> MTF must block.
        MultiTimeframeRouter mtfWithStub =
                new MultiTimeframeRouter(htf,
                        new RegimeRouter(new RegimeClassifier(),
                                new AlwaysFires(), new MeanReversionStrategy()));
        MultiTimeframeRouter.Decision d = mtfWithStub.decide(
                downtrend(250, 1000),
                uptrend(80, 100),
                uptrend(80, 100),
                10_000, risk);
        assertFalse(d.shouldEnter(), "HTF veto must override LTF signal");
        assertTrue(d.reason().startsWith("HTF blocked"));
    }
}
