package com.example.cryptobot.auth.entity;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 서비스 회원 엔티티.
 *
 * <p>소셜 로그인 사용자는 email/passwordHash 가 null 일 수 있습니다.
 * tier 는 FREE 기본, 관리자가 PREMIUM 으로 수동 변경합니다(데모 단계).</p>
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이메일 — 소셜 전용 계정은 null 가능 */
    @Column(unique = true, length = 255)
    private String email;

    /** BCrypt 해시된 비밀번호 — 소셜 전용 계정은 null */
    @Column(name = "password_hash")
    private String passwordHash;

    /** 표시 닉네임 */
    @Column(nullable = false, length = 50)
    private String nickname;

    /** 서비스 등급 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Tier tier = Tier.FREE;

    /** 계정 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    /** 온보딩 완료 시각 — null 이면 미완료 */
    @Column(name = "onboarded_at")
    private LocalDateTime onboardedAt;

    /** 동의한 약관 버전 */
    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    /** 이번 달 AI 리포트 생성 횟수 */
    @Column(name = "report_gen_count")
    @Builder.Default
    private int reportGenCount = 0;

    /** reportGenCount 가 속한 연월 (예: "2026-09") — 월 변경 시 카운트 리셋 기준 */
    @Column(name = "report_gen_month", length = 7)
    private String reportGenMonth;

    public enum Tier {
        FREE, PREMIUM
    }

    public enum Status {
        /** 정상 */
        ACTIVE,
        /** 탈퇴 처리(소프트 삭제) */
        WITHDRAWN
    }
}
