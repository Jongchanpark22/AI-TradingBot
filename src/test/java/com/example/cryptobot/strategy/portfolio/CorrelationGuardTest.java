package com.example.cryptobot.strategy.portfolio;

import com.example.cryptobot.market.candle.Candle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationGuardTest {

    private static Candle bar(LocalDateTime t, double close) {
        return Candle.builder()
                .symbol("X").period(Candle.CandlePeriod.ONE_HOUR)
                .timestamp(t)
                .openPrice(BigDecimal.valueOf(close))
                .highPrice(BigDecimal.valueOf(close))
                .lowPrice(BigDecimal.valueOf(close))
                .closePrice(BigDecimal.valueOf(close))
                .volume(BigDecimal.valueOf(1000))
                .quoteAssetVolume(BigDecimal.valueOf(1000 * close))
                .build();
    }

    /** Smooth uptrend; multiplies close by (1 + step) every bar. */
    private static List<Candle> trend(int n, double startPrice, double step) {
        List<Candle> out = new ArrayList<>();
        LocalDateTime t = LocalDateTime.of(2024, 1, 1, 0, 0);
        double p = startPrice;
        for (int i = 0; i < n; i++) {
            out.add(bar(t.plusHours(i), p));
            p *= (1 + step);
        }
        return out;
    }

    /** Random walk independent of any other series. */
    private static List<Candle> noise(int n, double startPrice, long seed) {
        Random r = new Random(seed);
        List<Candle> out = new ArrayList<>();
        LocalDateTime t = LocalDateTime.of(2024, 1, 1, 0, 0);
        double p = startPrice;
        for (int i = 0; i < n; i++) {
            out.add(bar(t.plusHours(i), p));
            p *= (1 + (r.nextDouble() - 0.5) * 0.02);
        }
        return out;
    }

    // ----- math sanity ---------------------------------------------------

    @Test
    void pearsonOfIdenticalSeriesIsOne() {
        double[] a = {1, 2, 3, 4, 5};
        assertEquals(1.0, CorrelationGuard.pearson(a, a), 1e-9);
    }

    @Test
    void pearsonOfNegatedSeriesIsMinusOne() {
        double[] a = {1, 2, 3, 4, 5};
        double[] b = {-1, -2, -3, -4, -5};
        assertEquals(-1.0, CorrelationGuard.pearson(a, b), 1e-9);
    }

    @Test
    void pearsonOfFlatSeriesIsZero() {
        double[] flat = {1, 1, 1, 1, 1};
        double[] varying = {1, 2, 3, 4, 5};
        assertEquals(0.0, CorrelationGuard.pearson(flat, varying), 1e-9);
    }

    // ----- guard behaviour -----------------------------------------------

    @Test
    void allowsCandidateWhenNoOpenPositions() {
        CorrelationGuard g = new CorrelationGuard();
        CorrelationGuard.Decision d = g.check("BTC", Map.of("BTC", trend(100, 100, 0.01)), List.of());
        assertTrue(d.allow());
        assertEquals(0.0, d.worstCorr(), 1e-9);
    }

    @Test
    void blocksWhenCandidateMatchesAnOpenPositionExactly() {
        CorrelationGuard g = new CorrelationGuard();
        // BTC and "BTC2" are literally the same series -> corr = 1
        List<Candle> series = trend(100, 100, 0.01);
        CorrelationGuard.Decision d = g.check("BTC2",
                Map.of("BTC", series, "BTC2", series),
                List.of("BTC"));
        assertFalse(d.allow());
        assertTrue(d.worstCorr() > 0.99);
    }

    @Test
    void allowsWhenCandidateIsUncorrelatedWithOpenPositions() {
        CorrelationGuard g = new CorrelationGuard();
        CorrelationGuard.Decision d = g.check("ETH",
                Map.of(
                        "BTC", noise(100, 100, 42),
                        "ETH", noise(100, 100, 9999)),
                List.of("BTC"));
        // independent random walks -> correlation should be small
        assertTrue(d.allow(), "uncorrelated random walks should not trip the 0.7 cap");
        assertTrue(Math.abs(d.worstCorr()) < 0.7);
    }

    @Test
    void allowsWhenCandidateHasInsufficientHistory() {
        CorrelationGuard g = new CorrelationGuard();
        // Only 10 bars — below the default 50 lookback.
        CorrelationGuard.Decision d = g.check("NEW",
                Map.of("BTC", trend(100, 100, 0.01),
                       "NEW", trend(10, 100, 0.01)),
                List.of("BTC"));
        assertTrue(d.allow());
    }

    @Test
    void requiresLookbackAtLeastFive() {
        assertThrows(IllegalArgumentException.class, () -> new CorrelationGuard(2, 0.5));
    }

    @Test
    void requiresMaxCorrInUnitInterval() {
        assertThrows(IllegalArgumentException.class, () -> new CorrelationGuard(50, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new CorrelationGuard(50, -0.1));
    }
}
