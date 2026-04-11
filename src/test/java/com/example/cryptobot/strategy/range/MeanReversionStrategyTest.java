package com.example.cryptobot.strategy.range;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.strategy.risk.EntryPlan;
import com.example.cryptobot.strategy.risk.RiskManager;
import com.example.cryptobot.strategy.risk.RiskParameters;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Strategy-level tests for {@link MeanReversionStrategy} that bypass the
 * regime classifier so we can craft inputs which satisfy the strategy's three
 * filters (lower BB pierce, RSI &lt; 30, bullish reversal candle) without
 * worrying about whether ADX would have classified the same data as
 * ranging.
 */
class MeanReversionStrategyTest {

    private final MeanReversionStrategy strategy = new MeanReversionStrategy();
    private final RiskManager risk = new RiskManager(RiskParameters.defaults());

    private static Candle bar(double o, double h, double l, double c) {
        return Candle.builder()
                .symbol("TEST").period(Candle.CandlePeriod.ONE_HOUR)
                .timestamp(LocalDateTime.now())
                .openPrice(o).highPrice(h).lowPrice(l).closePrice(c)
                .volume(1000.0).quoteAssetVolume(1000.0 * c)
                .build();
    }

    /**
     * 60 bars of steady decline followed by a single sharp reversal bar.
     * This produces:
     *   - low BB middle (mean is well above current price)
     *   - oversold Wilder RSI (long string of losses, single recent gain)
     *   - the final bar's low pierces the lower band and closes green
     */
    private static List<Candle> overSoldThenGreen() {
        List<Candle> out = new ArrayList<>();
        double price = 200.0;
        for (int i = 0; i < 60; i++) {
            double open = price;
            double close = price - 1.0;
            out.add(bar(open, open + 0.2, close - 0.2, close));
            price = close;
        }
        // final reversal: opens deep, low pierces below recent range, closes green above open
        double last = price;
        out.add(bar(last - 1.5, last + 0.5, last - 4.0, last - 0.2));
        return out;
    }

    @Test
    void firesOnOversoldGreenReversal() {
        Optional<EntryPlan> plan = strategy.evaluate(overSoldThenGreen(), 10_000, risk);
        assertTrue(plan.isPresent(), "MeanReversionStrategy should fire on textbook setup");
        EntryPlan p = plan.get();
        assertTrue(p.isExecutable());
        assertTrue(p.stopLossPrice() < p.entryPrice());
        assertTrue(p.takeProfitPrice() > p.entryPrice());
    }

    @Test
    void doesNotFireWhenLastBarIsRed() {
        // take the oversold setup and flip the last bar to a red candle
        List<Candle> c = overSoldThenGreen();
        Candle last = c.get(c.size() - 1);
        c.set(c.size() - 1, bar(
                last.getClosePrice() + 0.5,  // open above close -> red
                last.getHighPrice(),
                last.getLowPrice(),
                last.getClosePrice()));
        assertTrue(strategy.evaluate(c, 10_000, risk).isEmpty());
    }

    @Test
    void doesNotFireWithoutBollingerPierce() {
        // strong uptrend -> RSI is high and price is at the upper band
        List<Candle> c = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < 60; i++) {
            double open = price;
            double close = price + 1.0;
            c.add(bar(open, close + 0.2, open - 0.2, close));
            price = close;
        }
        assertTrue(strategy.evaluate(c, 10_000, risk).isEmpty());
    }

    @Test
    void doesNotFireWithInsufficientHistory() {
        List<Candle> c = new ArrayList<>();
        for (int i = 0; i < 5; i++) c.add(bar(100, 101, 99, 100.5));
        assertTrue(strategy.evaluate(c, 10_000, risk).isEmpty());
    }
}
