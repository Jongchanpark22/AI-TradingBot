package com.example.cryptobot.strategy.risk;

/**
 * Pure-function risk manager: turns market state + account equity into a
 * concrete {@link EntryPlan}, and turns an open position's running state into
 * an {@link TrailingDecision}.
 *
 * <p>Design notes:
 * <ul>
 *     <li><b>ATR-based stops:</b> the original bot used a fixed
 *         {@code -2.5% / +6.75%} stop and take-profit. That fails on both ends
 *         of the volatility spectrum (too tight on a high-vol day, too loose
 *         on a quiet one). The risk manager sizes the stop in ATR units so the
 *         distance auto-adapts to current volatility.</li>
 *     <li><b>Risk-based sizing:</b> position size is derived from
 *         {@code (equity * riskPerTrade) / stopDistance}, so a wider stop
 *         shrinks the position. Loss per trade is therefore bounded regardless
 *         of how wide the protective stop has to be.</li>
 *     <li><b>Trailing:</b> a Chandelier Exit
 *         ({@code highestHigh - trailingAtrMultiplier * atr}) is applied once
 *         the position is in profit. The stop is monotonically non-decreasing
 *         (it never moves against the position).</li>
 *     <li><b>Partial exit + break-even:</b> the first time price reaches
 *         {@code partialExitR} times the initial risk in profit, the manager
 *         signals to close 50% and move the stop to the entry price (locking
 *         in a "free" trade). This is the lever that lets the strategy "let
 *         winners run" while never giving back its initial risk.</li>
 * </ul>
 *
 * <p>This class is intentionally stateless and side-effect free so it can be
 * used both in production and inside the back-test engine.
 */
public final class RiskManager {

    private final RiskParameters params;

    public RiskManager(RiskParameters params) {
        this.params = params;
    }

    public RiskParameters params() {
        return params;
    }

    // ============================================================
    // Entry planning
    // ============================================================

    /**
     * Build an {@link EntryPlan} for a long entry.
     *
     * @param equity        current account equity (total balance)
     * @param entryPrice    fill price the executor will use
     * @param atr           current ATR (same units as price)
     * @return a plan that may be {@link EntryPlan#isExecutable() non-executable}
     *         when there is not enough room to size a positive quantity
     *         (e.g. ATR is zero / NaN, or risk per trade rounds to nothing)
     */
    public EntryPlan planLong(double equity, double entryPrice, double atr) {
        if (equity <= 0 || entryPrice <= 0 || !(atr > 0)) {
            return zeroPlan(entryPrice, atr);
        }

        double stopDistance = params.stopAtrMultiplier() * atr;
        double stopLoss = entryPrice - stopDistance;
        if (stopLoss <= 0) return zeroPlan(entryPrice, atr);

        double rewardDistance = stopDistance * params.takeProfitRMultiple();
        double takeProfit = entryPrice + rewardDistance;

        double riskAmount = equity * params.riskPerTrade();
        double quantity = riskAmount / stopDistance;
        double rewardAmount = quantity * rewardDistance;

        return new EntryPlan(quantity, entryPrice, stopLoss, takeProfit,
                riskAmount, rewardAmount, atr);
    }

    private EntryPlan zeroPlan(double entryPrice, double atr) {
        return new EntryPlan(0, entryPrice, 0, 0, 0, 0, atr);
    }

    // ============================================================
    // Trailing & partial-exit management
    // ============================================================

    /**
     * Compute the next trailing decision for an open long position.
     *
     * @param entryPrice           original fill price
     * @param initialStopLoss      original stop-loss price (from {@link EntryPlan})
     * @param currentStopLoss      stop-loss currently in force (may already
     *                             have been trailed up)
     * @param currentPrice         latest market price
     * @param highestPriceSeen     highest price observed since the position
     *                             opened (caller maintains this)
     * @param atr                  current ATR (used for the chandelier offset)
     * @param partialExitDone      true if the partial exit has already fired
     */
    public TrailingDecision updateTrailing(
            double entryPrice,
            double initialStopLoss,
            double currentStopLoss,
            double currentPrice,
            double highestPriceSeen,
            double atr,
            boolean partialExitDone
    ) {
        // ---- 1. immediate stop-loss / take-profit hit -------------------
        if (currentPrice <= currentStopLoss) {
            return new TrailingDecision(currentStopLoss, true, false, "stop-loss hit");
        }

        // ---- 2. partial exit at +1R ------------------------------------
        double initialRisk = entryPrice - initialStopLoss;
        if (!partialExitDone && initialRisk > 0) {
            double partialTarget = entryPrice + initialRisk * params.partialExitRMultiple();
            if (currentPrice >= partialTarget) {
                // close half and ratchet stop to break-even
                double newStop = Math.max(currentStopLoss, entryPrice);
                return new TrailingDecision(newStop, false, true,
                        "partial exit at " + params.partialExitRMultiple() + "R, stop -> break-even");
            }
        }

        // ---- 3. chandelier trailing stop -------------------------------
        if (atr > 0 && highestPriceSeen > entryPrice) {
            double chandelier = highestPriceSeen - params.trailingAtrMultiplier() * atr;
            // monotonically non-decreasing
            double newStop = Math.max(currentStopLoss, chandelier);
            if (newStop > currentStopLoss) {
                return new TrailingDecision(newStop, false, false, "trailing stop -> " + newStop);
            }
        }

        return TrailingDecision.hold(currentStopLoss);
    }

    // ============================================================
    // Equity / portfolio guards
    // ============================================================

    /**
     * Daily kill switch. Returns true when realised + open PnL for the day is
     * worse than {@code -maxDailyLoss * startOfDayEquity}, in which case all
     * new entries should be blocked until the next session.
     */
    public boolean isDailyLossBreached(double startOfDayEquity, double currentEquity) {
        if (startOfDayEquity <= 0) return false;
        double drawdown = (startOfDayEquity - currentEquity) / startOfDayEquity;
        return drawdown >= params.maxDailyLoss();
    }

    /** True when the portfolio is allowed to open another position. */
    public boolean canOpenAnotherPosition(int currentOpenPositions) {
        return currentOpenPositions < params.maxOpenPositions();
    }
}
