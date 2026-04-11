package com.example.cryptobot.strategy.core;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.common.entity.BaseEntity;
import com.example.cryptobot.market.candle.Candle;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "strategies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Strategy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StrategyType type;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StrategyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Candle.CandlePeriod targetPeriod;

    @Column(precision = 19, scale = 2)
    private BigDecimal maxOrderAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal maxDailyLoss;

    @Column(nullable = false)
    private Boolean useStopLoss;

    @Column(precision = 5, scale = 2)
    private BigDecimal stopLossPercent;

    @Column(nullable = false)
    private Boolean useTakeProfit;

    @Column(precision = 5, scale = 2)
    private BigDecimal takeProfitPercent;

    @Lob
    private String parameters;

    public enum StrategyType {
        MOVING_AVERAGE, RSI, VOLUME_BASED, MEAN_REVERSION, AI_BASED, HYBRID
    }

    public enum StrategyStatus {
        ACTIVE, INACTIVE, PAUSED
    }
}