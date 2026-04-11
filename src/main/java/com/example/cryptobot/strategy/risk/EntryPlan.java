package com.example.cryptobot.strategy.risk;

/**
 * Output of {@link RiskManager#planEntry}. Encapsulates everything the order
 * router needs in order to place an entry along with its protective stop and
 * take-profit.
 *
 * <p>{@code quantity} is computed from a fixed-fractional risk model:
 * <pre>
 *     riskAmount = equity * riskPerTrade
 *     quantity   = riskAmount / (entryPrice - stopLossPrice)
 * </pre>
 * which means a wider stop automatically yields a smaller position. This is the
 * single most important defence against the "fixed % stop on a volatile day"
 * failure mode the previous design had.
 */
public record EntryPlan(
        double quantity,
        double entryPrice,
        double stopLossPrice,
        double takeProfitPrice,
        double riskAmount,
        double rewardAmount,
        double atrAtEntry
) {
    public double riskRewardRatio() {
        return riskAmount == 0 ? 0 : rewardAmount / riskAmount;
    }

    public double notional() {
        return entryPrice * quantity;
    }

    /** {@code true} when the plan is sized to a strictly positive quantity. */
    public boolean isExecutable() {
        return quantity > 0
                && stopLossPrice > 0 && stopLossPrice < entryPrice
                && takeProfitPrice > entryPrice;
    }
}
