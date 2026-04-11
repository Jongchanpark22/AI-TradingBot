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
}