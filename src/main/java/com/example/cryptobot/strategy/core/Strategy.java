package com.example.cryptobot.strategy.core;

import com.example.cryptobot.market.candle.Candle;

import java.util.List;
import java.util.Optional;

/**
 * 전략 행동 인터페이스.
 *
 * <p>각 구현체는 캔들 목록만 받아 순수하게 신호를 판단한다.
 * 포지션 사이징/손절 계산은 {@code RiskManager}로 위임한다.
 * Spring {@code @Component}로 등록하면 {@link StrategyRegistry}가 자동 수집한다.
 *
 * <p>이전 {@code TradingStrategy} 인터페이스 대체 — equity/risk 파라미터를 제거하여
 * 전략 로직과 리스크 관리를 명확히 분리한다.
 */
public interface Strategy {

    /** 전략 고유 식별자 — 로그와 {@code StrategySignal}에 기록된다. */
    String id();

    /** 전략 행동 유형. */
    StrategyType type();

    /**
     * 캔들 히스토리를 기반으로 진입 신호를 산출한다.
     *
     * @param candles 최신 순서로 정렬된 캔들 목록 (oldest → newest)
     * @return 신호 존재 시 {@link StrategySignal}, 없으면 {@link Optional#empty()}
     */
    Optional<StrategySignal> evaluate(List<Candle> candles);
}
