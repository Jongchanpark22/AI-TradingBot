package com.example.cryptobot.chart;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 사용자 차트 주석·메모 엔티티.
 * 선 그리기(LINE), 자유 드로잉(DRAW), 텍스트 메모(MEMO) 지원.
 * pointsJson 예: [{"time":"2024-01-15","price":85000000},{"time":"2024-01-20","price":90000000}]
 */
@Entity
@Table(name = "user_chart_annotation", indexes = {
        @Index(name = "idx_uca_user_symbol", columnList = "user_id, symbol")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserChartAnnotation extends BaseEntity {

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

    /** 주석 유형 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnotationType type;

    /**
     * 좌표 포인트 JSON 배열.
     * 형식: [{"time":"2024-01-15","price":85000000}, ...]
     */
    @Column(name = "points_json", columnDefinition = "TEXT", nullable = false)
    private String pointsJson;

    /** 메모 텍스트 (MEMO 타입 또는 선 설명) */
    @Column(length = 500)
    private String note;

    public enum AnnotationType {
        LINE, DRAW, MEMO
    }
}
