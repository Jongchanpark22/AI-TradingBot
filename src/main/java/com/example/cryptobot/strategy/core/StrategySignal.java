package com.example.cryptobot.strategy.core;

/**
 * 전략이 산출하는 진입 신호.
 *
 * <p>포지션 사이징과 손절가는 포함하지 않는다 — {@code RiskManager.planLong()}으로
 * 위임되어 {@code RegimeRouter}에서 계산한다.
 *
 * @param direction   진입 방향 (현재 항상 LONG)
 * @param entryPrice  신호 발생 시점 진입 기준가
 * @param atr         신호 산출에 사용된 ATR (손절 계산용)
 * @param strategyId  전략 식별자 ({@link Strategy#id()})
 * @param strategyType 전략 행동 유형 ({@link StrategyType})
 */
public record StrategySignal(
        Direction direction,
        double entryPrice,
        double atr,
        String strategyId,
        StrategyType strategyType
) {
    public enum Direction { LONG, SHORT, FLAT }
}
