package com.example.cryptobot.holding;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 사용자 보유 종목 정보.
 * 평균 매수가, 수량을 관리하며 현재가는 API 호출 시 실시간 계산합니다.
 */
@Entity
@Table(name = "user_holding", indexes = {
        @Index(name = "idx_uh_user_id", columnList = "user_id"),
        @Index(name = "idx_uh_symbol", columnList = "symbol")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserHolding extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 보유자 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 마켓 코드 (예: KRW-BTC) */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** 평균 매수가 */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal avgBuyPrice;

    /** 보유 수량 */
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    /** 사용자 메모 (선택) */
    @Column(length = 500)
    private String memo;
}
