package com.example.cryptobot.news;

import java.util.List;

/**
 * 뉴스 소스 추상화 인터페이스.
 * 빅카인즈·네이버 뉴스 등 다양한 소스를 동일한 방식으로 수집합니다.
 */
public interface NewsSource {

    /** 이 소스의 식별자 (예: BIGKINDS, NAVER_NEWS) */
    String sourceName();

    /**
     * 특정 키워드로 뉴스를 수집합니다.
     *
     * @param keyword 검색어 (종목명, 테마어 등)
     * @param maxResults 최대 수집 건수
     * @return 수집된 NewsItem 목록 (미저장 상태)
     */
    List<NewsItem> collect(String keyword, int maxResults);
}
