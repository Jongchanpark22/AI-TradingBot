package com.example.cryptobot.holding;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 관심 종목 목록.
 * 보유하지 않지만 모니터링하고 싶은 종목을 등록합니다.
 */
@Entity
@Table(name = "watchlist", indexes = {
        @Index(name = "idx_wl_user_id", columnList = "user_id"),
        @Index(name = "idx_wl_symbol", columnList = "symbol")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Watchlist extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유자 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 마켓 코드 (예: KRW-BTC) */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** 사용자 메모 (선택) */
    @Column(length = 200)
    private String memo;
}
