package com.example.cryptobot.portfolio;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "positions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Position extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false)
    private String symbol; // BTC, ETH, etc.

    @Column(columnDefinition = "DECIMAL(19,8) DEFAULT 0")
    private Double quantity;

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private Double avgBuyPrice;

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private Double currentPrice;

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private Double unrealizedProfit;

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private Double unrealizedProfitRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionStatus status;

    public enum PositionStatus {
        OPEN, CLOSED, PARTIAL
    }

}

