package com.example.cryptobot.trade;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 거래 완료 기록 — 매수 진입부터 매도 청산까지 한 건의 거래 결과를 저장.
 * 손익, 청산 이유, 진입/청산 가격 등을 추적한다.
 */
@Entity
@Table(name = "trade_history", indexes = {
        @Index(name = "idx_th_symbol",    columnList = "symbol"),
        @Index(name = "idx_th_exit_time", columnList = "exit_time"),
        @Index(name = "idx_th_account",   columnList = "account_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false)
    private String symbol;

    /** 평균 매수가 */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal entryPrice;

    /** 실제 청산가 */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal exitPrice;

    /** 청산 수량 */
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    /** 매수 금액 = entryPrice × quantity */
    @Column(precision = 19, scale = 2)
    private BigDecimal entryAmount;

    /** 매도 금액 = exitPrice × quantity */
    @Column(precision = 19, scale = 2)
    private BigDecimal exitAmount;

    /** 손익 금액 = exitAmount - entryAmount */
    @Column(precision = 19, scale = 2)
    private BigDecimal profitAmount;

    /** 손익률 (%) = (exitPrice - entryPrice) / entryPrice × 100 */
    @Column(precision = 10, scale = 4)
    private BigDecimal profitRate;

    /** 포지션 진입 시점 */
    @Column(name = "entry_time")
    private LocalDateTime entryTime;

    /** 포지션 청산 시점 */
    @Column(name = "exit_time", nullable = false)
    private LocalDateTime exitTime;

    /**
     * 청산 유형
     * STOP_LOSS, TRAILING_STOP, TAKE_PROFIT, SELL_SIGNAL, PARTIAL_EXIT, MANUAL
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExitType exitType;

    /** 청산 상세 사유 (RiskManager reason 문자열 등) */
    @Column(length = 500)
    private String exitReason;

    /** 진입 시 ATR */
    private Double atrAtEntry;

    /** 포지션 유지 중 최고가 */
    private Double highestPrice;

    /** 부분 청산 여부 */
    @Builder.Default
    private Boolean partialExit = false;

    public enum ExitType {
        STOP_LOSS,       // 손절
        TRAILING_STOP,   // 트레일링 스탑
        TAKE_PROFIT,     // 목표가 달성 후 트레일링 하락
        SELL_SIGNAL,     // 전략 매도 신호
        PARTIAL_EXIT,    // 부분 청산
        MANUAL           // 수동
    }
}
