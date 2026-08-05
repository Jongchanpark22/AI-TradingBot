package com.example.cryptobot.report;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * AI 기업 리포트 보관함 엔티티.
 *
 * <p>userId는 3차 회원/인증 구현 전까지 Long 플레이스홀더로 유지합니다.
 * 3차에서 User 엔티티와 FK 연결 예정.</p>
 */
@Entity
@Table(name = "saved_report", indexes = {
        @Index(name = "idx_sr_user",   columnList = "user_id"),
        @Index(name = "idx_sr_target", columnList = "target_type, target_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 ID (3차 User 엔티티 연결 전까지 1L 기본값) */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 리포트 대상 유형 */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private TargetType targetType;

    /** 리포트 대상 코드 (corp_code, 지수 코드 등) */
    @Column(name = "target_code", nullable = false, length = 20)
    private String targetCode;

    /** 대상 이름 (기업명 등, 조회 편의용) */
    @Column(name = "target_name", length = 100)
    private String targetName;

    /** 사업연도 */
    @Column(name = "business_year")
    private Integer businessYear;

    /** LLM 생성 서술 텍스트 */
    @Column(name = "narrative", columnDefinition = "TEXT")
    private String narrative;

    /** 재무 지표 + 메타 JSON (AiReportResponse.financials 직렬화) */
    @Column(name = "content_json", columnDefinition = "TEXT")
    private String contentJson;

    /** 출처 메타 (DART 기준분기, API 버전 등) */
    @Column(name = "source_meta", length = 500)
    private String sourceMeta;

    public enum TargetType {
        STOCK, INDEX
    }
}
