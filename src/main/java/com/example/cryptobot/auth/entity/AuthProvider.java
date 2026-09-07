package com.example.cryptobot.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 소셜/이메일 인증 제공자 연결 엔티티.
 * 한 User 에 여러 provider 를 연결할 수 있습니다(계정 통합).
 */
@Entity
@Table(name = "auth_provider",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_provider_user",
                columnNames = {"provider", "provider_user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 연결된 서비스 회원 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 인증 제공자 종류 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Provider provider;

    /** 제공자가 발급한 고유 사용자 ID — 이메일 로그인은 null */
    @Column(name = "provider_user_id", length = 255)
    private String providerUserId;

    /** 연결 시각 */
    @Column(name = "linked_at", nullable = false)
    @Builder.Default
    private LocalDateTime linkedAt = LocalDateTime.now();

    public enum Provider {
        EMAIL, KAKAO, GOOGLE, APPLE
    }
}
