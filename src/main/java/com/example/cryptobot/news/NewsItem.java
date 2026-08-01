package com.example.cryptobot.news;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 뉴스 아이템 공통 모델.
 * 다양한 뉴스 소스(RSS, API 등)의 데이터를 단일 포맷으로 저장합니다.
 * themes/linkedSymbols JSON 필드는 추후 RAG 벡터 검색 연결을 위해 설계되었습니다.
 */
@Entity
@Table(name = "news_item", indexes = {
        @Index(name = "idx_ni_published", columnList = "published_at"),
        @Index(name = "idx_ni_source",    columnList = "source")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 뉴스 소스 식별자 (예: NAVER_FINANCE, GOOGLE_NEWS, DART) */
    @Column(nullable = false, length = 50)
    private String source;

    /** 기사 원문 URL (중복 방지 기준) */
    @Column(nullable = false, length = 500, unique = true)
    private String url;

    /** 기사 제목 */
    @Column(nullable = false, length = 500)
    private String title;

    /** 기사 요약 (선택) */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** 발행 시각 */
    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    /**
     * 관련 테마 태그 JSON 배열 (예: ["금리","연준","FOMC"]).
     * 추후 RAG 메타데이터 필터용.
     */
    @Column(columnDefinition = "TEXT")
    private String themes;

    /**
     * 연관 종목 코드 JSON 배열 (예: ["KRW-BTC","005930"]).
     * 종목별 뉴스 피드 필터링용.
     */
    @Column(columnDefinition = "TEXT")
    private String linkedSymbols;
}
