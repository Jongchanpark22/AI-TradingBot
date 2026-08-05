package com.example.cryptobot.news.dto;

import com.example.cryptobot.news.NewsItem;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 뉴스 피드 커서 페이지네이션 응답.
 */
@Getter
@Builder
public class NewsFeedResponse {

    /** 현재 페이지 뉴스 목록 */
    private List<NewsItem> items;

    /**
     * 다음 페이지 커서 토큰.
     * null이면 마지막 페이지.
     * 다음 요청 시 ?cursor={nextCursor} 로 전달.
     */
    private String nextCursor;

    /** 현재 페이지 건수 */
    private int size;
}
