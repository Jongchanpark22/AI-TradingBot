package com.example.cryptobot.alert;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 사용자가 정의한 커스텀 알림 조건.
 * conditionJson 예: {"indicator":"RSI","op":"<","value":30}
 */
@Entity
@Table(name = "user_alert")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAlert extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 ID (3차 User 엔티티 연결 전까지 1L 기본값) */
    @Column(name = "user_id", nullable = false)
    @Builder.Default
    private Long userId = 1L;

    /** 알림 타입 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private AlertType type = AlertType.INDICATOR;

    /** 감시할 심볼 (예: KRW-BTC) */
    @Column(nullable = false)
    private String symbol;

    /** 알림 이름 (사용자가 붙이는 레이블) */
    @Column
    private String name;

    /**
     * 알림 조건 JSON.
     * {"indicator":"RSI","op":"<","value":30}
     * {"indicator":"PRICE","op":">","value":50000000}
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String conditionJson;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 마지막 알림 발송 시각 (중복 발송 방지) */
    @Column
    private LocalDateTime lastFiredAt;

    /** 쿨다운: 마지막 발송 후 최소 대기 분 (기본 60분) */
    @Builder.Default
    @Column(nullable = false)
    private Integer cooldownMinutes = 60;

    public enum AlertType {
        PRICE, INDICATOR, NEWS
    }
}
