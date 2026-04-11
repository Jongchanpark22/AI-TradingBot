package com.example.cryptobot.strategy.indicator;

import com.example.cryptobot.market.candle.Candle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndicatorsTest {

    // ---- helpers ----------------------------------------------------------

    private static Candle bar(double open, double high, double low, double close, double vol) {
        return Candle.builder()
                .symbol("TEST")
                .period(Candle.CandlePeriod.ONE_HOUR)
                .timestamp(LocalDateTime.now())
                .openPrice(BigDecimal.valueOf(open))
                .highPrice(BigDecimal.valueOf(high))
                .lowPrice(BigDecimal.valueOf(low))
                .closePrice(BigDecimal.valueOf(close))
                .volume(BigDecimal.valueOf(vol))
                .quoteAssetVolume(BigDecimal.valueOf(vol * close))
                .build();
    }

    /** Generate a synthetic uptrend with mild noise. */
    private static List<Candle> uptrend(int n) {
        List<Candle> out = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < n; i++) {
            double open = price;
            double close = price + 1.0;          // +1 every bar
            double high = close + 0.3;
            double low = open - 0.3;
            out.add(bar(open, high, low, close, 1000));
            price = close;
        }
        return out;
    }

    private static List<Candle> downtrend(int n) {
        List<Candle> out = new ArrayList<>();
        double price = 200.0;
        for (int i = 0; i < n; i++) {
            double open = price;
            double close = price - 1.0;
            double high = open + 0.3;
            double low = close - 0.3;
            out.add(bar(open, high, low, close, 1000));
            price = close;
        }
        return out;
    }

    private static List<Candle> sideways(int n) {
        List<Candle> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double mid = 100.0;
            double close = mid + ((i % 2 == 0) ? 0.5 : -0.5);
            double open = mid;
            out.add(bar(open, mid + 1, mid - 1, close, 1000));
        }
        return out;
    }

    // ---- SMA / EMA --------------------------------------------------------

    @Test
    void smaReturnsArithmeticMeanOfLastPeriod() {
        List<Double> v = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        assertEquals(4.0, Indicators.sma(v, 3), 1e-9); // (3+4+5)/3
    }

    @Test
    void smaReturnsNaNWhenInsufficient() {
        assertTrue(Double.isNaN(Indicators.sma(List.of(1.0, 2.0), 5)));
    }

    @Test
    void emaConvergesTowardsSeriesValue() {
        List<Double> v = new ArrayList<>();
        for (int i = 0; i < 50; i++) v.add(10.0);
        assertEquals(10.0, Indicators.ema(v, 10), 1e-9);
    }

    // ---- RSI --------------------------------------------------------------

    @Test
    void rsiOnPureUptrendIs100() {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < 30; i++) closes.add(100.0 + i);
        assertEquals(100.0, Indicators.rsi(closes, 14), 1e-6);
    }

    @Test
    void rsiOnPureDowntrendIsNearZero() {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < 30; i++) closes.add(200.0 - i);
        assertEquals(0.0, Indicators.rsi(closes, 14), 1e-6);
    }

    @Test
    void rsiNeutralWhenInsufficientData() {
        assertEquals(50.0, Indicators.rsi(List.of(1.0, 2.0), 14));
    }

    // ---- ATR --------------------------------------------------------------

    @Test
    void atrIsPositiveOnTrendingData() {
        double a = Indicators.atr(uptrend(60), 14);
        assertFalse(Double.isNaN(a));
        assertTrue(a > 0);
    }

    @Test
    void atrIsNaNWhenNotEnoughBars() {
        assertTrue(Double.isNaN(Indicators.atr(uptrend(5), 14)));
    }

    // ---- ADX --------------------------------------------------------------

    @Test
    void adxFlagsTrendingMarket() {
        Indicators.AdxValue up = Indicators.adx(uptrend(80), 14);
        assertTrue(up.isValid(), "ADX should be computable");
        assertTrue(up.adx() > 25, "Pure uptrend should yield strong ADX, got " + up.adx());
        assertTrue(up.plusDi() > up.minusDi(), "+DI should dominate in uptrend");
    }

    @Test
    void adxFlagsRangingMarketAsWeak() {
        Indicators.AdxValue side = Indicators.adx(sideways(80), 14);
        assertTrue(side.isValid());
        assertTrue(side.adx() < 25, "Sideways market should yield weak ADX, got " + side.adx());
    }

    @Test
    void adxOnDowntrendHasMinusDiDominant() {
        Indicators.AdxValue d = Indicators.adx(downtrend(80), 14);
        assertTrue(d.isValid());
        assertTrue(d.minusDi() > d.plusDi(), "-DI should dominate in downtrend");
    }

    // ---- Bollinger -------------------------------------------------------

    @Test
    void bollingerWrapsCloses() {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < 30; i++) closes.add(100.0 + (i % 2 == 0 ? 1 : -1));
        Indicators.BollingerBand b = Indicators.bollinger(closes, 20, 2.0);
        assertTrue(b.upper() > b.middle());
        assertTrue(b.middle() > b.lower());
        assertEquals(100.0, b.middle(), 0.5);
    }

    // ---- Donchian --------------------------------------------------------

    @Test
    void donchianCapturesExtremes() {
        List<Candle> c = uptrend(30);
        Indicators.DonchianChannel d = Indicators.donchian(c, 20);
        // upper = highest high in last 20
        double expectedHigh = Double.NEGATIVE_INFINITY;
        double expectedLow = Double.POSITIVE_INFINITY;
        for (int i = c.size() - 20; i < c.size(); i++) {
            expectedHigh = Math.max(expectedHigh, c.get(i).getHighPrice().doubleValue());
            expectedLow = Math.min(expectedLow, c.get(i).getLowPrice().doubleValue());
        }
        assertEquals(expectedHigh, d.upper(), 1e-9);
        assertEquals(expectedLow, d.lower(), 1e-9);
    }

    @Test
    void donchianExcludingLastIgnoresCurrentBar() {
        List<Candle> c = uptrend(30);
        Indicators.DonchianChannel d = Indicators.donchianExcludingLast(c, 20);
        // The latest bar's high (highest of all) must not be considered
        assertTrue(d.upper() < c.get(c.size() - 1).getHighPrice().doubleValue());
    }

    // ---- Supertrend ------------------------------------------------------

    @Test
    void supertrendDirectionMatchesTrend() {
        Indicators.SupertrendPoint up = Indicators.supertrend(uptrend(60), 10, 3.0);
        Indicators.SupertrendPoint dn = Indicators.supertrend(downtrend(60), 10, 3.0);
        assertNotNull(up);
        assertNotNull(dn);
        assertTrue(up.isUp(), "Supertrend should be up on uptrend");
        assertFalse(dn.isUp(), "Supertrend should be down on downtrend");
    }

    @Test
    void supertrendIsNullWhenNotEnoughData() {
        assertNull(Indicators.supertrend(uptrend(5), 10, 3.0));
    }
}
