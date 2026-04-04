package com.example.cryptobot.risk;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "risk_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false)
    private LocalDate tradeDate;

    @Column(precision = 19, scale = 2)
    private BigDecimal dailyLoss;

    @Column(precision = 19, scale = 2)
    private BigDecimal dailyProfit;

    @Column(nullable = false)
    private Boolean isMaxLossExceeded;

    @Column(nullable = false)
    private Boolean isTradingAllowed;

    private String remark;
}