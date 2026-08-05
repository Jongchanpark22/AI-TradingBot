package com.example.cryptobot.chart;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 사용자 커스텀 지표 설정 엔티티.
 * paramsJson 예: {"period":14,"overbought":70,"oversold":30} (RSI 파라미터)
 */
@Entity
@Table(name = "user_indicator_setting", indexes = {
        @Index(name = "idx_uis_user_symbol", columnList = "user_id, symbol")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserIndicatorSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 ID (3차 User 엔티티 연결 전까지 1L 기본값) */
    @Column(name = "user_id", nullable = false)
    @Builder.Default
    private Long userId = 1L;

    /** 심볼 (예: KRW-BTC) */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** 지표 타입 (예: RSI, EMA, MACD, BOLLINGER) */
    @Column(name = "indicator_type", nullable = false, length = 30)
    private String indicatorType;

    /** 지표 파라미터 JSON */
    @Column(name = "params_json", columnDefinition = "TEXT")
    private String paramsJson;

    /** 활성화 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}
