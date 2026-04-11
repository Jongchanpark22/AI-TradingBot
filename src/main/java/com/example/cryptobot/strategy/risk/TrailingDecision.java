package com.example.cryptobot.strategy.risk;

/**
 * Result of {@link RiskManager#updateTrailing}. Tells the executor what (if
 * anything) needs to change about an open position right now.
 *
 * @param newStopLoss          updated stop-loss price (>= the previous stop;
 *                             stops never move against the position)
 * @param shouldExitNow        true when current price has hit the (possibly
 *                             trailed) stop or take-profit and the position
 *                             should be flat-closed
 * @param shouldPartialExit    true when price reached the partial-exit R
 *                             multiple for the first time and 50% should be
 *                             closed (caller is responsible for marking the
 *                             position so this fires only once)
 * @param reason               human-readable explanation, useful for logs
 */
public record TrailingDecision(
        double newStopLoss,
        boolean shouldExitNow,
        boolean shouldPartialExit,
        String reason
) {
    public static TrailingDecision hold(double stop) {
        return new TrailingDecision(stop, false, false, "hold");
    }
}
