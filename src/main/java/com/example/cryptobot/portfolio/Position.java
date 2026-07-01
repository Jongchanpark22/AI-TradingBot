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

    // ====== Risk-management state (Phase 2) ======
    // Populated by RiskManager and updated by the trailing/partial-exit loop.
    // All nullable so legacy positions remain valid.

    /** Initial protective stop set at entry. */
    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double initialStopLoss;

    /** Currently active stop, monotonically non-decreasing for longs. */
    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double currentStopLoss;

    /** Original take-profit target. */
    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double takeProfitPrice;

    /** Highest price observed since the position opened, used for chandelier exit. */
    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double highestPriceSinceEntry;

    /** ATR sampled at entry, kept for diagnostics / R-multiple math. */
    @Column(columnDefinition = "DECIMAL(19,8)")
    private Double atrAtEntry;

    /** Set to true once the +1R partial exit has fired. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean partialExitDone = false;

    /** Phase 0: 이 포지션을 연 신호 ID — 청산 시 trade_history.signal_id로 전달 */
    @Column(name = "signal_id", length = 36)
    private String signalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionStatus status;

    public enum PositionStatus {
        OPEN, CLOSED, PARTIAL
    }

}

