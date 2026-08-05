package com.example.cryptobot.news;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 뉴스 테마·종목 구독 설정 엔티티.
 * themes/symbols는 JSON 배열 문자열.
 * 예: themes=["금리","FOMC"], symbols=["KRW-BTC","005930"]
 */
@Entity
@Table(name = "news_preference", indexes = {
        @Index(name = "idx_np_user", columnList = "user_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsPreference extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 ID (3차 User 엔티티 연결 전까지 1L 기본값, 유니크) */
    @Column(name = "user_id", nullable = false, unique = true)
    @Builder.Default
    private Long userId = 1L;

    /**
     * 구독 테마 JSON 배열.
     * 예: ["금리","연준","비트코인","반도체"]
     */
    @Column(name = "themes", columnDefinition = "TEXT")
    private String themes;

    /**
     * 구독 종목 코드 JSON 배열.
     * 예: ["KRW-BTC","005930","035420"]
     */
    @Column(name = "symbols", columnDefinition = "TEXT")
    private String symbols;
}
