package com.example.cryptobot.news;

import com.example.cryptobot.news.dto.NewsFeedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 뉴스·공시 조회 API.
 *
 * <p>⚠️ 면책: 제공되는 뉴스·공시 정보는 참고용이며 투자 권유가 아닙니다.</p>
 */
@RestController
@RequestMapping("/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    /**
     * 최근 DART 공시 목록 조회.
     *
     * @param days 최근 N일 (기본 7일, 최대 30일)
     */
    @GetMapping("/disclosures")
    public List<DartDisclosure> getDisclosures(
            @RequestParam(defaultValue = "7") int days) {
        return newsService.getRecentDisclosures(Math.min(days, 30));
    }

    /**
     * 특정 심볼 DART 공시 조회.
     *
     * @param symbol 마켓 코드 (예: KRW-BTC)
     */
    @GetMapping("/disclosures/{symbol}")
    public List<DartDisclosure> getDisclosuresBySymbol(@PathVariable String symbol) {
        return newsService.getDisclosuresBySymbol(symbol);
    }

    /**
     * 최근 뉴스 목록 조회 (기존 호환용).
     *
     * @param hours 최근 N시간 (기본 24시간, 최대 72시간)
     */
    @GetMapping("/items")
    public List<NewsItem> getNews(
            @RequestParam(defaultValue = "24") int hours) {
        return newsService.getRecentNews(Math.min(hours, 72));
    }

    /**
     * 뉴스 피드 (커서 페이지네이션).
     * 종목·테마 필터는 저장 시점이 아닌 조회 시점에 적용됩니다.
     *
     * @param cursor  이전 응답의 nextCursor (생략 시 첫 페이지)
     * @param size    페이지 크기 (기본 20, 최대 50)
     * @param sort    정렬 기준: latest(최신순, 기본) | views(조회수순)
     * @param symbols 종목 코드 필터 (예: KRW-BTC) — 생략 시 전체
     * @param themes  테마 키워드 필터 (예: 금리) — 생략 시 전체
     */
    @GetMapping("/feed")
    public NewsFeedResponse getFeed(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) String symbols,
            @RequestParam(required = false) String themes) {
        return newsService.getNewsFeed(cursor, size, sort, symbols, themes);
    }

    /**
     * 뉴스 단건 조회 (조회수 +1).
     *
     * @param id 뉴스 아이템 ID
     */
    @GetMapping("/items/{id}")
    public ResponseEntity<NewsItem> getNewsItem(@PathVariable Long id) {
        return newsService.getNewsItem(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
