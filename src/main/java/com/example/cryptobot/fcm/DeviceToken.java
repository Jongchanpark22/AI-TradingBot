package com.example.cryptobot.fcm;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * FCM 디바이스 토큰 엔티티.
 * 사용자당 여러 디바이스(앱 재설치·멀티 디바이스)를 지원합니다.
 */
@Entity
@Table(name = "device_token", indexes = {
        @Index(name = "idx_dt_user_id", columnList = "user_id"),
        @Index(name = "idx_dt_token", columnList = "token", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 디바이스 소유자 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** FCM 등록 토큰 (디바이스당 고유) */
    @Column(nullable = false, unique = true, length = 512)
    private String token;

    /** 디바이스 유형 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private DeviceType deviceType = DeviceType.ANDROID;

    public enum DeviceType {
        ANDROID, IOS, WEB
    }
}
