package com.example.cryptobot.search;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 종목 검색 API.
 *
 * <p>코인은 업비트 마켓 목록에서, 국내주식은 DART 공시 기업명 검색을 통해 조회합니다.</p>
 * <p>⚠️ 검색 결과는 정보 제공 목적이며 투자 권유가 아닙니다.</p>
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 종목 통합 검색.
     *
     * @param q      검색어 (이름·코드 부분 일치, 필수)
     * @param market 검색 범위: coin(코인) | kr(국내주식) | all(전체, 기본값)
     * @return 검색 결과 목록 [{code, name, type, stockCode}]
     */
    @GetMapping
    public List<SearchResult> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "all") String market) {
        return searchService.search(q, market);
    }
}
