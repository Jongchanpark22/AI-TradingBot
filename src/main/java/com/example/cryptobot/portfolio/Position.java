package com.example.cryptobot.portfolio;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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
    private String symbol;

    @Column(columnDefinition = "DECIMAL(19,8) DEFAULT 0")
    private BigDecimal quantity;

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private BigDecimal avgBuyPrice;

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private BigDecimal currentPrice;

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private BigDecimal unrealizedProfit;

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private BigDecimal unrealizedProfitRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionStatus status;

    public enum PositionStatus {
        OPEN, CLOSED, PARTIAL
    }

}

