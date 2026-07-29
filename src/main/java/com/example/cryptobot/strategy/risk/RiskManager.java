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
     * <p>청산 사다리:
     * <ol>
     *   <li>+1R 도달 시 1차 부분청산 ({@code partialExitRatio}) + 손절 → 진입가(본전)</li>
     *   <li>+2R 도달 시 2차 부분청산 ({@code partial2Ratio}) + 트레일링 강화 ({@code trailAtrMultAfterP2})</li>
     *   <li>나머지 25%는 강화 트레일링으로 유지</li>
     * </ol>
     * 주의: 2차 부분청산은 {@code takeProfitRMultiple > partial2RMultiple}일 때만 정상 동작.
     * (현재 TP=3R > P2=2R이므로 aboveProfitTarget 진입 전에 2차 청산이 먼저 실행됨.)
     *
     * @param entryPrice           original fill price
     * @param initialStopLoss      original stop-loss price (from {@link EntryPlan})
     * @param currentStopLoss      stop-loss currently in force (may already
     *                             have been trailed up)
     * @param currentPrice         latest market price
     * @param highestPriceSeen     highest price observed since the position
     *                             opened (caller maintains this)
     * @param atr                  current ATR (used for the chandelier offset)
     * @param partialExitDone      true if the 1차 partial exit has already fired
     * @param secondPartialDone    true if the 2차 partial exit has already fired
     */
    public TrailingDecision updateTrailing(
            double entryPrice,
            double initialStopLoss,
            double currentStopLoss,
            double currentPrice,
            double highestPriceSeen,
            double atr,
            boolean partialExitDone,
            boolean secondPartialDone
    ) {
        // ---- 1. immediate stop-loss hit -------------------
        if (currentPrice <= currentStopLoss) {
            return new TrailingDecision(currentStopLoss, true, false, false, "stop-loss hit");
        }

        double initialRisk = entryPrice - initialStopLoss;

        // ---- 2. 1차 부분청산: +1R ------------------------------------
        if (!partialExitDone && initialRisk > 0) {
            double partialTarget = entryPrice + initialRisk * params.partialExitRMultiple();
            if (currentPrice >= partialTarget) {
                // 손절선 → 진입가(본전)로 이동
                double newStop = Math.max(currentStopLoss, entryPrice);
                return new TrailingDecision(newStop, false, true, false,
                        "partial exit at " + params.partialExitRMultiple() + "R, stop -> break-even");
            }
        }

        // ---- 3. 2차 부분청산: +2R (1차 완료 후) ------------------------------------
        if (partialExitDone && !secondPartialDone && initialRisk > 0 && params.partial2RMultiple() > 0) {
            double secondTarget = entryPrice + initialRisk * params.partial2RMultiple();
            if (currentPrice >= secondTarget) {
                double newStop = Math.max(currentStopLoss, entryPrice);
                return new TrailingDecision(newStop, false, false, true,
                        "second partial exit at " + params.partial2RMultiple()
                                + "R, trailing -> " + params.trailAtrMultAfterP2() + "xATR");
            }
        }

        // ---- 4. 챈들리어 트레일링 -----------------------------------------------
        // 2차 부분청산 완료 후: trailAtrMultAfterP2(강화), 미완료: trailingAtrMultiplier(기본)
        // ATR이 0이면 최고가의 3% 고정 폴백 사용
        if (highestPriceSeen > entryPrice) {
            double activeMult = secondPartialDone
                    ? params.trailAtrMultAfterP2()
                    : params.trailingAtrMultiplier();
            double trailingOffset = atr > 0
                    ? activeMult * atr
                    : highestPriceSeen * 0.03;
            double chandelier = highestPriceSeen - trailingOffset;
            // 손절선은 단조증가 (절대 뒤로 이동하지 않음)
            double newStop = Math.max(currentStopLoss, chandelier);
            if (newStop > currentStopLoss) {
                return new TrailingDecision(newStop, false, false, false, "trailing stop -> " + newStop);
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
