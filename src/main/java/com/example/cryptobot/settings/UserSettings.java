package com.example.cryptobot.settings;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 사용자 앱 설정 엔티티.
 * 테마·알림 종류별 on/off를 저장합니다.
 */
@Entity
@Table(name = "user_settings", indexes = {
        @Index(name = "idx_us_user_id", columnList = "user_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유자 ID (유니크) */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /**
     * 앱 테마.
     * SYSTEM: 기기 설정 따름 (기본값)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Theme theme = Theme.SYSTEM;

    /** 가격 알림 활성 여부 */
    @Column(nullable = false)
    @Builder.Default
    private boolean priceAlertEnabled = true;

    /** 뉴스 알림 활성 여부 */
    @Column(nullable = false)
    @Builder.Default
    private boolean newsAlertEnabled = true;

    /** AI 리포트 완료 알림 활성 여부 */
    @Column(nullable = false)
    @Builder.Default
    private boolean reportAlertEnabled = true;

    public enum Theme {
        LIGHT, DARK, SYSTEM
    }
}
