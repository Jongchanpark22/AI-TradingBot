package com.example.cryptobot.strategy.regime;

/**
 * Coarse classification of the current market state. Drives which strategy
 * the {@link RegimeRouter} delegates to.
 *
 * <ul>
 *     <li>{@link #TRENDING_UP} / {@link #TRENDING_DOWN} — strong directional
 *         move. Use breakout / trend-following logic. ADX is high and the
 *         dominant DI line points up (or down).</li>
 *     <li>{@link #RANGING} — choppy / mean-reverting. ADX is low. Use
 *         mean-reversion logic (Bollinger / RSI).</li>
 *     <li>{@link #NEUTRAL} — between regimes. Best to stand aside; the bot
 *         will not open new positions in this state.</li>
 * </ul>
 */
public enum MarketRegime {
    TRENDING_UP,
    TRENDING_DOWN,
    RANGING,
    NEUTRAL;

    public boolean isTrending() {
        return this == TRENDING_UP || this == TRENDING_DOWN;
    }
}
