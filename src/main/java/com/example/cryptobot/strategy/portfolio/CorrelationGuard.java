package com.example.cryptobot.strategy.portfolio;

import com.example.cryptobot.market.candle.Candle;

import java.util.List;
import java.util.Map;

/**
 * Portfolio-level guard that blocks a candidate entry when its returns are
 * already too correlated with one or more open positions.
 *
 * <p>The motivating problem: a multi-symbol crypto bot can happily go long
 * BTC, ETH, and SOL at the same time because each symbol passes its own
 * single-name regime/risk filter — but those three are 0.85+ correlated on
 * intraday returns, so the portfolio is effectively making one bet with 3x
 * the size and 3x the drawdown risk. The guard caps that explicitly.
 *
 * <h3>Method</h3>
 * <ol>
 *   <li>For each symbol, take the most recent {@code lookback} log-returns
 *       from its candle history.</li>
 *   <li>Compute pairwise Pearson correlation between the candidate and every
 *       open position.</li>
 *   <li>If <em>any</em> pair exceeds {@code maxAbsCorrelation}, block the
 *       entry. (We use abs() so an inverse-correlated short would also
 *       count, even though the current bot only goes long.)</li>
 * </ol>
 *
 * <p>Pure-function and stateless, so it can be unit-tested in isolation and
 * called from both the live executor and the back-test.
 */
public final class CorrelationGuard {

    private final int lookback;
    private final double maxAbsCorrelation;

    public CorrelationGuard() {
        this(50, 0.7);
    }

    /**
     * @param lookback           number of recent returns to use; must be >= 5
     * @param maxAbsCorrelation  block when |corr| with any open symbol is
     *                           strictly greater than this value; must be in
     *                           [0, 1]
     */
    public CorrelationGuard(int lookback, double maxAbsCorrelation) {
        if (lookback < 5) throw new IllegalArgumentException("lookback must be >= 5");
        if (maxAbsCorrelation < 0 || maxAbsCorrelation > 1)
            throw new IllegalArgumentException("maxAbsCorrelation must be in [0, 1]");
        this.lookback = lookback;
        this.maxAbsCorrelation = maxAbsCorrelation;
    }

    public int lookback() { return lookback; }
    public double maxAbsCorrelation() { return maxAbsCorrelation; }

    /**
     * Decide whether the {@code candidateSymbol} can be added.
     *
     * @param candidateSymbol     symbol about to be entered
     * @param candleHistory       map of symbol -> candle history (oldest first).
     *                            Must contain the candidate and every open
     *                            symbol; missing entries are treated as
     *                            "not enough data" and do not block.
     * @param openSymbols         symbols currently held by the portfolio
     * @return a decision with the worst correlation observed
     */
    public Decision check(String candidateSymbol,
                          Map<String, List<Candle>> candleHistory,
                          List<String> openSymbols) {
        if (candidateSymbol == null || candleHistory == null || openSymbols == null) {
            return Decision.allow(0, "no inputs");
        }
        if (openSymbols.isEmpty()) {
            return Decision.allow(0, "no open positions");
        }
        double[] candidateReturns = returns(candleHistory.get(candidateSymbol));
        if (candidateReturns == null) {
            return Decision.allow(0, "candidate has insufficient history");
        }

        double worst = 0;
        String worstSymbol = null;
        for (String s : openSymbols) {
            if (s == null || s.equals(candidateSymbol)) continue;
            double[] other = returns(candleHistory.get(s));
            if (other == null) continue;
            double c = pearson(candidateReturns, other);
            if (Math.abs(c) > Math.abs(worst)) {
                worst = c;
                worstSymbol = s;
            }
        }

        if (Math.abs(worst) > maxAbsCorrelation) {
            return Decision.block(worst,
                    "correlation with " + worstSymbol + " = " + format(worst)
                            + " > " + maxAbsCorrelation);
        }
        return Decision.allow(worst, worstSymbol == null
                ? "no comparable open symbols"
                : "max corr = " + format(worst) + " (" + worstSymbol + ")");
    }

    // ============================================================
    // Math
    // ============================================================

    private double[] returns(List<Candle> candles) {
        if (candles == null || candles.size() < lookback + 1) return null;
        int from = candles.size() - (lookback + 1);
        double[] r = new double[lookback];
        for (int i = 0; i < lookback; i++) {
            double p0 = candles.get(from + i).getClosePrice().doubleValue();
            double p1 = candles.get(from + i + 1).getClosePrice().doubleValue();
            if (p0 <= 0 || p1 <= 0) return null;
            r[i] = Math.log(p1 / p0);
        }
        return r;
    }

    /** Pearson correlation. Returns 0 when either series has zero variance. */
    static double pearson(double[] a, double[] b) {
        if (a.length != b.length || a.length < 2) return 0;
        double sumA = 0, sumB = 0;
        for (int i = 0; i < a.length; i++) { sumA += a[i]; sumB += b[i]; }
        double meanA = sumA / a.length;
        double meanB = sumB / b.length;
        double cov = 0, varA = 0, varB = 0;
        for (int i = 0; i < a.length; i++) {
            double da = a[i] - meanA;
            double db = b[i] - meanB;
            cov += da * db;
            varA += da * da;
            varB += db * db;
        }
        if (varA == 0 || varB == 0) return 0;
        return cov / Math.sqrt(varA * varB);
    }

    private static String format(double v) {
        return String.format("%.3f", v);
    }

    /**
     * Result of a {@link CorrelationGuard#check} call.
     *
     * @param allow         whether the candidate may be added
     * @param worstCorr     signed correlation with the most-correlated open
     *                      symbol (or 0 when no comparison was possible)
     * @param reason        human-readable explanation
     */
    public record Decision(boolean allow, double worstCorr, String reason) {
        public static Decision allow(double c, String r) { return new Decision(true, c, r); }
        public static Decision block(double c, String r) { return new Decision(false, c, r); }
    }
}
