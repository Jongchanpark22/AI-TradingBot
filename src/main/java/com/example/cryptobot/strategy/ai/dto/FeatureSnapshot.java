package com.example.cryptobot.strategy.ai.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Python ai-service와의 계약 feature vector.
 *
 * <p>모든 필드명은 Python serve.py / data_loader.py의 JSON 키와 정확히 일치해야 한다.
 * HybridStrategyExecutor(라이브)와 HybridBacktestEngine(백테스트) 양쪽에서
 * {@link com.example.cryptobot.strategy.ai.FeatureSnapshotFactory}를 통해 생성된다.
 *
 * <p>직렬화 시 Jackson 기본 camelCase 동작을 사용한다(@JsonProperty 불필요).
 */
@Data
@Builder
public class FeatureSnapshot {

    // ---- 메타 ----
    private String symbol;
    /** 타임프레임 이름 (예: "15분", "1시간") */
    private String timeframe;
    /** 신호 봉의 Unix 타임스탬프 (ms) — Python SQL ORDER BY 기준 */
    private long timestampMs;
    /** 신호 봉 종가 */
    private double close;

    // ---- 이동평균 ----
    private double ema12;
    private double ema26;
    private double sma50;
    /** (close - ema26) / ema26 × 100 — EMA 이격률 (%) */
    private double closeVsEma26Pct;

    // ---- MACD (12-26-9) ----
    private double macd;
    private double macdSignal;
    private double macdHist;

    // ---- RSI(14) ----
    private double rsi14;

    // ---- ADX(14) ----
    private double adx14;
    private double plusDi;
    private double minusDi;

    // ---- 변동성 ----
    /** ATR(14) */
    private double atr14;
    private double bbUpper;
    private double bbLower;

    // ---- 거래량 ----
    /** 현재 거래량 / 20봉 이동평균 거래량 */
    private double volumeRatio;

    // ---- 구조 지표 ----
    /** Donchian(20) 상단 — 직전 20봉 기준 (look-ahead 방지용 excludeLast) */
    private double donchianHigh20;
    /** Supertrend(10, 3.0) 방향: 1=상승, -1=하락, 0=미결정 */
    private int supertrendDir;

    // ---- 레짐 ----
    /** RegimeClassifier 결과 (TRENDING_UP / RANGING / NEUTRAL / TRENDING_DOWN) */
    private String regime;

    // ---- HybridSignalAnalyzer 신호 점수 ----
    /** STRONG_UPTREND=2, UPTREND=1, SIDEWAYS=0, DOWNTREND=-1, STRONG_DOWNTREND=-2 */
    private int trendScore;
    /** STRONG_BUY=2, BUY=1, NEUTRAL=0, SELL=-1, STRONG_SELL=-2 */
    private int momentumScore;
    /** WEAK_BUY=1, OVERBOUGHT=-1, else=0 */
    private int rsiScore;
    /** STRONG_BULLISH/HAMMER/CONSECUTIVE_BULLISH=1, STRONG_BEARISH/SHOOTING_STAR=-1, else=0 */
    private int candleScore;
    /** VERY_LOW=0, LOW=1, NORMAL=2, HIGH=3, VERY_HIGH=4 */
    private int volumeConfidence;
    /** bullishScore 합계 (HybridSignalAnalyzer.TradeSignal.score) */
    private int totalScore;

    // ---- 최종 룰 신호 ----
    /** HybridSignalAnalyzer.generateTradeSignal() 결과: STRONG_BUY / BUY / NO_SIGNAL 등 */
    private String ruleSignal;
}
