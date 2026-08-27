package com.example.cryptobot.news;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

    boolean existsByUrl(String url);

    List<NewsItem> findByPublishedAtGreaterThanEqualOrderByPublishedAtDesc(LocalDateTime from);

    List<NewsItem> findBySourceOrderByPublishedAtDesc(String source);

    // ─── 커서 페이지네이션 — symbols/themes 필터 포함 ────────────────────────

    /**
     * 최신순 커서 페이지네이션 (선택적 종목·테마 LIKE 필터).
     * symbol/theme이 null이면 해당 조건을 무시합니다.
     */
    @Query("""
        SELECT n FROM NewsItem n
        WHERE (:cursorAt IS NULL OR n.publishedAt < :cursorAt
               OR (n.publishedAt = :cursorAt AND n.id < :cursorId))
        AND (:symbol IS NULL OR n.linkedSymbols LIKE :symbol)
        AND (:theme  IS NULL OR n.themes        LIKE :theme)
        ORDER BY n.publishedAt DESC, n.id DESC
        """)
    List<NewsItem> findByLatestCursorFiltered(
            @Param("cursorAt")  LocalDateTime cursorAt,
            @Param("cursorId")  Long cursorId,
            @Param("symbol")    String symbol,
            @Param("theme")     String theme,
            Pageable pageable);

    /**
     * 조회수 내림차순 커서 페이지네이션 (선택적 종목·테마 LIKE 필터).
     */
    @Query("""
        SELECT n FROM NewsItem n
        WHERE (:cursorViews IS NULL OR n.viewCount < :cursorViews
               OR (n.viewCount = :cursorViews AND n.id < :cursorId))
        AND (:symbol IS NULL OR n.linkedSymbols LIKE :symbol)
        AND (:theme  IS NULL OR n.themes        LIKE :theme)
        ORDER BY n.viewCount DESC, n.id DESC
        """)
    List<NewsItem> findByViewsCursorFiltered(
            @Param("cursorViews") Long cursorViews,
            @Param("cursorId")    Long cursorId,
            @Param("symbol")      String symbol,
            @Param("theme")       String theme,
            Pageable pageable);

    /** 조회수 +1 */
    @Modifying
    @Query("UPDATE NewsItem n SET n.viewCount = n.viewCount + 1 WHERE n.id = :id")
    void incrementViewCount(@Param("id") Long id);

    /** 보존 기간 초과 뉴스 일괄 삭제, 삭제 건수 반환. */
    @Modifying
    @Query("DELETE FROM NewsItem n WHERE n.publishedAt < :cutoff")
    int deleteByPublishedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
