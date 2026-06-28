package com.example.cryptobot.strategy.ai.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 기술적 지표 스냅샷 — ML 모델에 전달하는 feature vector.
 *
 * <p>HybridStrategyExecutor에서 이미 계산된 값만 담는다 (재계산 금지).
 * Python 서버로 전송하고 strategy_run_logs.feature_json에 직렬화하여 저장한다.
 */
@Data
@Builder
public class FeatureSnapshot {

    // ---- 메타 ----
    private String symbol;
    private String period;

    // ---- 추세 지표 ----
    private double ema12;
    private double ema26;
    private double sma50;

    // ---- 모멘텀 지표 (MACD 12-26-9) ----
    private double macd;
    private double macdSignal;
    private double macdHistogram;

    // ---- RSI(14) ----
    private double rsi;

    // ---- 거래량 ----
    /** 현재 거래량 / 20봉 이동평균 거래량 */
    private double volumeRatio;

    // ---- 변동성 ----
    /** ATR(14) */
    private double atr;

    // ---- 레짐 ----
    /** RegimeClassifier 결과 (TRENDING_UP / RANGING / NEUTRAL / TRENDING_DOWN) */
    private String regime;

    // ---- HybridSignalAnalyzer 중간 분류 ----
    private String trendSignal;
    private String momentumSignal;
    private String rsiSignal;
    private String volumeSignal;
    private String candleSignal;

    // ---- 최종 신호 (필터 적용 전 원 신호) ----
    /** HybridSignalAnalyzer.generateTradeSignal() 결과 (STRONG_BUY / BUY / NO_SIGNAL 등) */
    private String rawSignal;

    /** 복합 점수 (bullishScore 또는 bearishScore) */
    private Integer signalScore;
}
