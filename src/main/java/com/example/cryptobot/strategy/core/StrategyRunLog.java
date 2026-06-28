package com.example.cryptobot.strategy.core;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "strategy_run_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyRunLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long strategyId;

    @Column(nullable = false)
    private String strategyName;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String period;

    private Double ema12;
    private Double ema26;
    private Double sma50;
    private Double rsi;
    private Double macd;
    private Double signalLine;
    private Double volumeRatio;

    @Column(nullable = false)
    private String trendSignal;

    @Column(nullable = false)
    private String momentumSignal;

    @Column(nullable = false)
    private String rsiSignal;

    @Column(nullable = false)
    private String volumeSignal;

    @Column(nullable = false)
    private String candleSignal;

    @Column(nullable = false)
    private String finalSignal;

    private Integer confidence;

    @Column(length = 1000)
    private String reason;

    @Column(nullable = false)
    private Boolean orderCreated;

    @Column(length = 1000)
    private String blockedReason;

    // ---- Phase 0: AI 연동 / 학습 데이터 필드 ----

    /** 신호 고유 ID (UUID) — trade_history.signal_id와 조인하여 feature-결과 연결 */
    @Column(length = 36)
    private String signalId;

    /** FeatureSnapshot JSON 직렬화 — Python 학습 데이터 원본 */
    @Column(columnDefinition = "TEXT")
    private String featureJson;

    /** ML 모델 매수 확률 (0.0~1.0). RULE_ONLY 모드나 ML 서버 다운 시 NULL. */
    private Double mlBuyProb;

    /** 추론에 사용된 ML 모델 버전. NULL이면 ML 미사용. */
    @Column(length = 64)
    private String mlModelVer;
}