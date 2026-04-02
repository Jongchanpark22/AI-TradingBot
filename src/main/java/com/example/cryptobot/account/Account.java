package com.example.cryptobot.account;

import com.example.cryptobot.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @JsonIgnore
    @Column(nullable = false)
    private String apiKey;

    @JsonIgnore
    @Column(nullable = false)
    private String secretKey;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalBalance;

    @Column(precision = 19, scale = 2)
    private BigDecimal availableBalance;

    @Column(precision = 19, scale = 2)
    private BigDecimal lockedBalance;
}