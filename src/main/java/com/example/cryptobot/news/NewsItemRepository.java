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

    // ─── 커서 페이지네이션 (keyset) ────────────────────────────────────────

    /** 최신순 커서 페이지네이션: publishedAt < cursor 이거나 같으면 id < cursorId */
    @Query("""
        SELECT n FROM NewsItem n
        WHERE (:cursorAt IS NULL OR n.publishedAt < :cursorAt
               OR (n.publishedAt = :cursorAt AND n.id < :cursorId))
        ORDER BY n.publishedAt DESC, n.id DESC
        """)
    List<NewsItem> findByLatestCursor(
            @Param("cursorAt") LocalDateTime cursorAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    /** 조회수 내림차순 커서 페이지네이션 */
    @Query("""
        SELECT n FROM NewsItem n
        WHERE (:cursorViews IS NULL OR n.viewCount < :cursorViews
               OR (n.viewCount = :cursorViews AND n.id < :cursorId))
        ORDER BY n.viewCount DESC, n.id DESC
        """)
    List<NewsItem> findByViewsCursor(
            @Param("cursorViews") Long cursorViews,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    /** 조회수 +1 */
    @Modifying
    @Query("UPDATE NewsItem n SET n.viewCount = n.viewCount + 1 WHERE n.id = :id")
    void incrementViewCount(@Param("id") Long id);
}
