package com.example.cryptobot.strategy.core;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private Double maxOrderAmount;

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private Double maxDailyLoss;

    @Column(nullable = false)
    private Boolean useStopLoss;

    @Column(columnDefinition = "DECIMAL(5,2) DEFAULT 2.0")
    private Double stopLossPercent;

    @Column(nullable = false)
    private Boolean useTakeProfit;

    @Column(columnDefinition = "DECIMAL(5,2) DEFAULT 5.0")
    private Double takeProfitPercent;

    private String parameters; // JSON format

    public enum StrategyType {
        MOVING_AVERAGE, RSI, VOLUME_BASED, MEAN_REVERSION, AI_BASED
    }

    public enum StrategyStatus {
        ACTIVE, INACTIVE, PAUSED
    }

}

