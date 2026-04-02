package com.example.cryptobot.account;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "accounts", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "exchange_type"})
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExchangeType exchangeType;

    @Column(nullable = false)
    private String apiKey;

    @Column(nullable = false)
    private String secretKey;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private Double totalBalance;

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private Double availableBalance;

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private Double lockedBalance;

    public enum ExchangeType {
        UPBIT, BITHUMB
    }

}

