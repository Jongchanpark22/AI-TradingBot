package com.example.cryptobot.news;

import lombok.RequiredArgsConstructor;
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
     * 최근 뉴스 목록 조회.
     *
     * @param hours 최근 N시간 (기본 24시간, 최대 72시간)
     */
    @GetMapping("/items")
    public List<NewsItem> getNews(
            @RequestParam(defaultValue = "24") int hours) {
        return newsService.getRecentNews(Math.min(hours, 72));
    }
}
