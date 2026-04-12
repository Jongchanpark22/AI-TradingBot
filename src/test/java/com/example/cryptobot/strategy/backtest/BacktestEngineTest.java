package com.example.cryptobot.strategy.backtest;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.range.MeanReversionStrategy;
import com.example.cryptobot.strategy.regime.RegimeClassifier;
import com.example.cryptobot.strategy.regime.RegimeRouter;
import com.example.cryptobot.strategy.risk.RiskManager;
import com.example.cryptobot.strategy.risk.RiskParameters;
import com.example.cryptobot.strategy.trend.DonchianBreakoutStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BacktestEngineTest {

    private static Candle bar(LocalDateTime t, double o, double h, double l, double c, double v) {
        return Candle.builder()
                .symbol("TEST").period(Candle.CandlePeriod.ONE_HOUR)
                .timestamp(t)
                .openPrice(BigDecimal.valueOf(o))
                .highPrice(BigDecimal.valueOf(h))
                .lowPrice(BigDecimal.valueOf(l))
                .closePrice(BigDecimal.valueOf(c))
                .volume(BigDecimal.valueOf(v))
                .quoteAssetVolume(BigDecimal.valueOf(v * c))
                .build();
    }

    /** Long, smooth uptrend with elevated volume on every bar. Designed to be tradable. */
    private static List<Candle> bullRun(int n) {
        List<Candle> out = new ArrayList<>();
        double price = 100.0;
        LocalDateTime t = LocalDateTime.of(2024, 1, 1, 0, 0);
        for (int i = 0; i < n; i++) {
            double open = price;
            double close = price + 1.5;          // strong, steady uptrend
            double high = close + 0.5;
            double low = open - 0.3;
            out.add(bar(t.plusHours(i), open, high, low, close, 1000));
            price = close;
        }
        return out;
    }

    /** Long sideways drift — stays under ADX trending threshold. */
    private static List<Candle> chop(int n) {
        List<Candle> out = new ArrayList<>();
        LocalDateTime t = LocalDateTime.of(2024, 1, 1, 0, 0);
        for (int i = 0; i < n; i++) {
            // tiny zig-zag around 100
            double mid = 100 + (i % 2 == 0 ? 0.05 : -0.05);
            out.add(bar(t.plusHours(i), mid, mid + 0.1, mid - 0.1, mid + 0.02, 1000));
        }
        return out;
    }

    private static RegimeRouter newRouter() {
        // Loose volume filter (1.0x SMA) so synthetic constant-volume series
        // are tradable; production callers stick with the 1.3x default.
        return new RegimeRouter(
                new RegimeClassifier(),
                new DonchianBreakoutStrategy(20, 14, 10, 3.0, 20, 1.0),
                new MeanReversionStrategy());
    }

    // -----------------------------------------------------------------

    @Test
    void runOnEmptyOrShortInputReturnsFlatResult() {
        BacktestEngine eng = new BacktestEngine(newRouter(), new RiskManager(RiskParameters.defaults()), 14, 50);
        BacktestResult r = eng.run("TEST", List.of(), 10_000);
        assertEquals(10_000, r.startingEquity());
        assertEquals(10_000, r.finalEquity());
        assertEquals(0, r.totalTrades());
        assertEquals(0.0, r.totalReturn(), 1e-9);
        assertTrue(r.equityCurve().isEmpty());
    }

    @Test
    void bullRunProducesPositiveExpectancyAndAtLeastOneTrade() {
        BacktestEngine eng = new BacktestEngine(newRouter(), new RiskManager(RiskParameters.defaults()), 14, 50);
        BacktestResult r = eng.run("TEST", bullRun(300), 10_000);

        assertTrue(r.totalTrades() >= 1, "router should fire on a steady uptrend");
        assertTrue(r.finalEquity() >= r.startingEquity(),
                "a tradable bull run should not lose money: " + r.finalEquity());
        assertTrue(r.expectancyR() >= 0,
                "expectancy in R should be non-negative on a clean trend: " + r.expectancyR());
        // every observed bar produces an equity-curve point
        assertEquals(300 - 50, r.equityCurve().size());
    }

    @Test
    void chopProducesNoCatastrophicLoss() {
        // The point isn't to be profitable in chop — it's to verify the stop /
        // sizing logic prevents a runaway loss.
        BacktestEngine eng = new BacktestEngine(newRouter(), new RiskManager(RiskParameters.defaults()), 14, 50);
        BacktestResult r = eng.run("TEST", chop(300), 10_000);

        // Worst-case allowable: a few losing trades at 1% risk each.
        assertTrue(r.finalEquity() > 9_000,
                "chop should not blow up the account: " + r.finalEquity());
        assertTrue(r.maxDrawdown() < 0.10);
    }

    @Test
    void summaryStatsAreInternallyConsistent() {
        BacktestEngine eng = new BacktestEngine(newRouter(), new RiskManager(RiskParameters.defaults()), 14, 50);
        BacktestResult r = eng.run("TEST", bullRun(300), 10_000);

        assertEquals(r.totalTrades(), r.winningTrades() + r.losingTrades());
        if (r.totalTrades() > 0) {
            assertEquals((double) r.winningTrades() / r.totalTrades(), r.winRate(), 1e-9);
        }
        // total return matches starting/final equity
        assertEquals((r.finalEquity() / r.startingEquity()) - 1.0, r.totalReturn(), 1e-9);
        // drawdown is in [0, 1]
        assertTrue(r.maxDrawdown() >= 0 && r.maxDrawdown() <= 1);
    }
}
