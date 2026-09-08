package com.example.cryptobot.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 리프레시 토큰 엔티티.
 *
 * <p>실제 토큰 값 대신 SHA-256 해시를 저장하여 DB 유출 시 토큰 재사용을 방지합니다.
 * rotation 정책: 재발급 시 이전 토큰을 revoke 하고 새 토큰을 발급합니다.</p>
 */
@Entity
@Table(name = "refresh_token", indexes = {
        @Index(name = "idx_rt_user_id", columnList = "user_id"),
        @Index(name = "idx_rt_token_hash", columnList = "token_hash", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 토큰 소유 사용자 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256(토큰 원문) — 원문은 클라이언트에만 존재 */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** 만료 시각 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** revoke 여부 — true 이면 사용 불가 */
    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    /** 발급 시각 */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
