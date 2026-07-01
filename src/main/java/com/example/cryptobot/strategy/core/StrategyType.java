package com.example.cryptobot.strategy.core;

/**
 * 전략의 행동 유형 분류.
 *
 * <p>각 {@link Strategy} 구현체가 반환하며, {@code StrategyRunLog} 등 ML 학습 데이터에 기록된다.
 */
public enum StrategyType {
    TREND_FOLLOWING,
    MEAN_REVERSION,
    BREAKOUT,
    SCALPING
}
