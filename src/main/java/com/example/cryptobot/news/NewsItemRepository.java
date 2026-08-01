package com.example.cryptobot.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

    boolean existsByUrl(String url);

    List<NewsItem> findByPublishedAtGreaterThanEqualOrderByPublishedAtDesc(LocalDateTime from);

    List<NewsItem> findBySourceOrderByPublishedAtDesc(String source);
}
