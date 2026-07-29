package com.example.cryptobot.strategy.risk;

/**
 * Result of {@link RiskManager#updateTrailing}. Tells the executor what (if
 * anything) needs to change about an open position right now.
 *
 * @param newStopLoss               updated stop-loss price (>= the previous stop;
 *                                  stops never move against the position)
 * @param shouldExitNow             true when current price has hit the (possibly
 *                                  trailed) stop or take-profit and the position
 *                                  should be flat-closed
 * @param shouldPartialExit         true when price reached +1R for the first time:
 *                                  close partialExitRatio of remaining qty
 * @param shouldSecondPartialExit   true when price reached +2R (after 1차 완료):
 *                                  close partial2Ratio of remaining qty, then tighten trailing
 * @param reason                    human-readable explanation, useful for logs
 */
public record TrailingDecision(
        double newStopLoss,
        boolean shouldExitNow,
        boolean shouldPartialExit,
        boolean shouldSecondPartialExit,
        String reason
) {
    public static TrailingDecision hold(double stop) {
        return new TrailingDecision(stop, false, false, false, "hold");
    }
}
