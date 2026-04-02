package com.example.cryptobot.order;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_account_id", columnList = "account_id"),
    @Index(name = "idx_symbol", columnList = "symbol"),
    @Index(name = "idx_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSide side;

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private Double price;

    @Column(columnDefinition = "DECIMAL(19,8) DEFAULT 0")
    private Double quantity;

    @Column(columnDefinition = "DECIMAL(19,8) DEFAULT 0")
    private Double filledQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(unique = true)
    private String exchangeOrderId;

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private Double totalAmount;

    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    private Double filledAmount;

    @Column(columnDefinition = "DECIMAL(19,4) DEFAULT 0")
    private Double fee;

    private String remark;

    public enum OrderType {
        LIMIT, MARKET
    }

    public enum OrderSide {
        BUY, SELL
    }

    public enum OrderStatus {
        PENDING, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED
    }

}

