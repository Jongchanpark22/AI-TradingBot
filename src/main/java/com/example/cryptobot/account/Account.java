package com.example.cryptobot.account;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Boolean isActive;

    @Builder.Default
    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal totalBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal lockedBalance = BigDecimal.ZERO;
}