package com.example.cryptobot.strategy.indicator;

import com.example.cryptobot.market.candle.Candle;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless technical indicator library.
 *
 * <p>All methods accept a {@link List} of {@link Candle} ordered from oldest
 * (index 0) to newest (last index). Methods return either the latest value
 * (or NaN when there is not enough data) or a {@code List} aligned 1:1 with
 * the input — values that cannot yet be computed are filled with NaN.
 *
 * <p>Conventions:
 * <ul>
 *     <li>RSI / ATR / ADX use Wilder's smoothing (standard reference).</li>
 *     <li>Methods are pure and allocation-light so they can be reused
 *         inside back-test loops without performance surprises.</li>
 *     <li>No mutation of inputs.</li>
 * </ul>
 */
public final class Indicators {

    private Indicators() {}

    // ============================================================
    // Moving averages
    // ============================================================

    /** Simple moving average over the last {@code period} closes. NaN if insufficient data. */
    public static double sma(List<Double> values, int period) {
        if (values == null || values.size() < period || period <= 0) return Double.NaN;
        double sum = 0;
        for (int i = values.size() - period; i < values.size(); i++) sum += values.get(i);
        return sum / period;
    }

    /**
     * Exponential moving average over the entire input, seeded with the SMA of
     * the first {@code period} values.
     */
    public static double ema(List<Double> values, int period) {
        if (values == null || values.size() < period || period <= 0) return Double.NaN;
        double k = 2.0 / (period + 1);
        // seed with SMA of first `period`
        double seed = 0;
        for (int i = 0; i < period; i++) seed += values.get(i);
        double e = seed / period;
        for (int i = period; i < values.size(); i++) {
            e = (values.get(i) - e) * k + e;
        }
        return e;
    }

    // ============================================================
    // True Range / ATR (Wilder)
    // ============================================================

    /** True Range at index {@code i} ({@code i >= 1}). */
    public static double trueRange(List<Candle> candles, int i) {
        Candle cur = candles.get(i);
        Candle prev = candles.get(i - 1);
        double hl = high(cur) - low(cur);
        double hc = Math.abs(high(cur) - close(prev));
        double lc = Math.abs(low(cur) - close(prev));
        return Math.max(hl, Math.max(hc, lc));
    }

    /**
     * ATR series using Wilder smoothing. Output is aligned to {@code candles};
     * positions before {@code period} are {@link Double#NaN}.
     */
    public static List<Double> atrSeries(List<Candle> candles, int period) {
        int n = candles == null ? 0 : candles.size();
        List<Double> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(Double.NaN);
        if (n <= period) return out;

        double sum = 0;
        for (int i = 1; i <= period; i++) sum += trueRange(candles, i);
        double prev = sum / period;
        out.set(period, prev);
        for (int i = period + 1; i < n; i++) {
            double tr = trueRange(candles, i);
            prev = (prev * (period - 1) + tr) / period;
            out.set(i, prev);
        }
        return out;
    }

    /** Latest ATR value, or NaN. */
    public static double atr(List<Candle> candles, int period) {
        List<Double> s = atrSeries(candles, period);
        return s.isEmpty() ? Double.NaN : s.get(s.size() - 1);
    }

    // ============================================================
    // RSI (Wilder)
    // ============================================================

    /**
     * Wilder RSI on close prices. Returns 50 (neutral) when not enough data.
     */
    public static double rsi(List<Double> closes, int period) {
        if (closes == null || closes.size() <= period || period <= 0) return 50.0;
        double gain = 0, loss = 0;
        for (int i = 1; i <= period; i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            if (diff >= 0) gain += diff; else loss -= diff;
        }
        double avgGain = gain / period;
        double avgLoss = loss / period;
        for (int i = period + 1; i < closes.size(); i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            double g = diff > 0 ? diff : 0;
            double l = diff < 0 ? -diff : 0;
            avgGain = (avgGain * (period - 1) + g) / period;
            avgLoss = (avgLoss * (period - 1) + l) / period;
        }
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    // ============================================================
    // ADX (Wilder)
    // ============================================================

    /** Result of an ADX computation. */
    public record AdxValue(double adx, double plusDi, double minusDi) {
        public boolean isValid() {
            return !Double.isNaN(adx) && !Double.isNaN(plusDi) && !Double.isNaN(minusDi);
        }
    }

    /**
     * ADX with +DI / -DI lines. Needs at least {@code 2*period + 1} candles.
     * Trending market is conventionally {@code adx > 25}.
     */
    public static AdxValue adx(List<Candle> candles, int period) {
        int n = candles == null ? 0 : candles.size();
        if (n < 2 * period + 1) return new AdxValue(Double.NaN, Double.NaN, Double.NaN);

        // Initial Wilder sums over i=1..period
        double trSum = 0, plusSum = 0, minusSum = 0;
        for (int i = 1; i <= period; i++) {
            trSum += trueRange(candles, i);
            plusSum += plusDM(candles, i);
            minusSum += minusDM(candles, i);
        }

        double lastPlusDi = 0, lastMinusDi = 0;
        double adxVal = Double.NaN;
        double dxAccum = 0;
        int dxCount = 0;

        for (int i = period + 1; i < n; i++) {
            trSum = trSum - trSum / period + trueRange(candles, i);
            plusSum = plusSum - plusSum / period + plusDM(candles, i);
            minusSum = minusSum - minusSum / period + minusDM(candles, i);

            lastPlusDi = trSum == 0 ? 0 : 100.0 * plusSum / trSum;
            lastMinusDi = trSum == 0 ? 0 : 100.0 * minusSum / trSum;
            double sumDi = lastPlusDi + lastMinusDi;
            double dx = sumDi == 0 ? 0 : 100.0 * Math.abs(lastPlusDi - lastMinusDi) / sumDi;

            if (dxCount < period) {
                dxAccum += dx;
                dxCount++;
                if (dxCount == period) adxVal = dxAccum / period;
            } else {
                adxVal = (adxVal * (period - 1) + dx) / period;
            }
        }
        return new AdxValue(adxVal, lastPlusDi, lastMinusDi);
    }

    private static double plusDM(List<Candle> c, int i) {
        double up = high(c.get(i)) - high(c.get(i - 1));
        double dn = low(c.get(i - 1)) - low(c.get(i));
        return (up > dn && up > 0) ? up : 0;
    }

    private static double minusDM(List<Candle> c, int i) {
        double up = high(c.get(i)) - high(c.get(i - 1));
        double dn = low(c.get(i - 1)) - low(c.get(i));
        return (dn > up && dn > 0) ? dn : 0;
    }

    // ============================================================
    // Bollinger Bands
    // ============================================================

    public record BollingerBand(double upper, double middle, double lower, double bandwidth) {
        /** Bandwidth normalized by middle — useful as a volatility / squeeze proxy. */
        public double percentBandwidth() {
            return middle == 0 ? Double.NaN : (upper - lower) / middle;
        }
    }

    /** Bollinger band at the latest bar. {@code stdDevMul} is typically 2.0. */
    public static BollingerBand bollinger(List<Double> closes, int period, double stdDevMul) {
        if (closes == null || closes.size() < period) {
            return new BollingerBand(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        double mid = sma(closes, period);
        double sq = 0;
        for (int i = closes.size() - period; i < closes.size(); i++) {
            double d = closes.get(i) - mid;
            sq += d * d;
        }
        double sd = Math.sqrt(sq / period);
        double upper = mid + stdDevMul * sd;
        double lower = mid - stdDevMul * sd;
        return new BollingerBand(upper, mid, lower, upper - lower);
    }

    // ============================================================
    // Donchian Channel
    // ============================================================

    public record DonchianChannel(double upper, double middle, double lower) {}

    /**
     * Donchian channel over the last {@code period} candles (inclusive of the
     * latest bar). For classic turtle breakout signals callers usually want
     * the channel computed over the prior {@code period} bars excluding the
     * current bar — use {@link #donchianExcludingLast(List, int)} for that.
     */
    public static DonchianChannel donchian(List<Candle> candles, int period) {
        if (candles == null || candles.size() < period) {
            return new DonchianChannel(Double.NaN, Double.NaN, Double.NaN);
        }
        double hi = Double.NEGATIVE_INFINITY;
        double lo = Double.POSITIVE_INFINITY;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            hi = Math.max(hi, high(candles.get(i)));
            lo = Math.min(lo, low(candles.get(i)));
        }
        return new DonchianChannel(hi, (hi + lo) / 2.0, lo);
    }

    /** Donchian channel over the {@code period} bars *before* the latest bar. */
    public static DonchianChannel donchianExcludingLast(List<Candle> candles, int period) {
        if (candles == null || candles.size() < period + 1) {
            return new DonchianChannel(Double.NaN, Double.NaN, Double.NaN);
        }
        double hi = Double.NEGATIVE_INFINITY;
        double lo = Double.POSITIVE_INFINITY;
        int end = candles.size() - 1; // exclude last
        for (int i = end - period; i < end; i++) {
            hi = Math.max(hi, high(candles.get(i)));
            lo = Math.min(lo, low(candles.get(i)));
        }
        return new DonchianChannel(hi, (hi + lo) / 2.0, lo);
    }

    // ============================================================
    // Supertrend
    // ============================================================

    public record SupertrendPoint(double value, int direction) {
        /** {@code +1} when the trend is up (close above lower band), {@code -1} when down. */
        public boolean isUp() { return direction > 0; }
    }

    /**
     * Supertrend series. Common defaults: {@code period=10, multiplier=3.0}.
     * Output is aligned to input; bars before {@code period} are {@code null}.
     */
    public static List<SupertrendPoint> supertrendSeries(List<Candle> candles, int period, double multiplier) {
        int n = candles == null ? 0 : candles.size();
        List<SupertrendPoint> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(null);
        if (n <= period) return out;

        List<Double> atr = atrSeries(candles, period);
        double finalUpper = 0;
        double finalLower = 0;

        for (int i = period; i < n; i++) {
            Candle c = candles.get(i);
            double a = atr.get(i);
            double hl2 = (high(c) + low(c)) / 2.0;
            double basicUpper = hl2 + multiplier * a;
            double basicLower = hl2 - multiplier * a;

            if (i == period) {
                finalUpper = basicUpper;
                finalLower = basicLower;
                int dir0 = close(c) > basicUpper ? 1 : -1;
                double st0 = dir0 == 1 ? finalLower : finalUpper;
                out.set(i, new SupertrendPoint(st0, dir0));
                continue;
            }

            double prevClose = close(candles.get(i - 1));
            finalUpper = (basicUpper < finalUpper || prevClose > finalUpper) ? basicUpper : finalUpper;
            finalLower = (basicLower > finalLower || prevClose < finalLower) ? basicLower : finalLower;

            SupertrendPoint prev = out.get(i - 1);
            int dir;
            if (prev.direction() == -1) {
                dir = close(c) > finalUpper ? 1 : -1;
            } else {
                dir = close(c) < finalLower ? -1 : 1;
            }
            double st = dir == 1 ? finalLower : finalUpper;
            out.set(i, new SupertrendPoint(st, dir));
        }
        return out;
    }

    /** Latest Supertrend point or {@code null} if not enough data. */
    public static SupertrendPoint supertrend(List<Candle> candles, int period, double multiplier) {
        List<SupertrendPoint> s = supertrendSeries(candles, period, multiplier);
        return s.isEmpty() ? null : s.get(s.size() - 1);
    }

    // ============================================================
    // Helpers
    // ============================================================

    /** Extract closing prices from a candle list. */
    public static List<Double> closes(List<Candle> candles) {
        List<Double> out = new ArrayList<>(candles.size());
        for (Candle c : candles) out.add(close(c));
        return out;
    }

    /** Extract volumes from a candle list. */
    public static List<Double> volumes(List<Candle> candles) {
        List<Double> out = new ArrayList<>(candles.size());
        for (Candle c : candles) out.add(c.getVolume() == null ? 0.0 : c.getVolume().doubleValue());
        return out;
    }

    // BigDecimal → primitive accessors. Indicators are happiest in double space;
    // entities persist BigDecimal for monetary precision.
    private static double high(Candle c)  { return c.getHighPrice().doubleValue(); }
    private static double low(Candle c)   { return c.getLowPrice().doubleValue(); }
    private static double close(Candle c) { return c.getClosePrice().doubleValue(); }
}
