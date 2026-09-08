package com.example.cryptobot.search;

import com.example.cryptobot.exchange.upbit.client.UpbitApiClient;
import com.example.cryptobot.exchange.upbit.dto.UpbitMarketDto;
import com.example.cryptobot.news.DartApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 종목 검색 서비스.
 * 코인은 업비트 마켓 목록에서, 국내주식은 DART 공시 API에서 검색합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final UpbitApiClient upbitApiClient;
    private final DartApiClient dartApiClient;

    /**
     * 종목을 검색합니다.
     *
     * @param q      검색어 (이름·코드 부분 일치)
     * @param market "coin" | "kr" | "all"
     * @return 검색 결과 목록
     */
    public List<SearchResult> search(String q, String market) {
        if (q == null || q.isBlank()) {
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();

        if ("coin".equals(market) || "all".equals(market)) {
            results.addAll(searchCoins(q.trim()));
        }
        if ("kr".equals(market) || "all".equals(market)) {
            results.addAll(searchKrStocks(q.trim()));
        }

        return results;
    }

    /**
     * 업비트 KRW 마켓에서 코인을 검색합니다.
     * market 코드·한국어명·영문명을 모두 비교합니다 (대소문자 무시).
     */
    private List<SearchResult> searchCoins(String q) {
        String lower = q.toLowerCase(Locale.ROOT);
        try {
            return upbitApiClient.getAllKrwMarketDtos().stream()
                    .filter(m -> matchesCoin(m, lower))
                    .map(m -> new SearchResult(
                            m.getMarket(),
                            m.getKoreanName(),
                            SearchResult.Type.COIN,
                            null
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("코인 검색 실패: q={}", q, e);
            return List.of();
        }
    }

    /**
     * DART 공시 API에서 기업명으로 국내주식을 검색합니다.
     */
    private List<SearchResult> searchKrStocks(String q) {
        try {
            return dartApiClient.searchByCorpName(q).stream()
                    .map(item -> new SearchResult(
                            item.corpCode(),
                            item.corpName(),
                            SearchResult.Type.KR_STOCK,
                            item.stockCode()
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("국내주식 검색 실패: q={}", q, e);
            return List.of();
        }
    }

    /** 코인 DTO와 검색어가 일치하는지 확인합니다 (대소문자 무시). */
    private boolean matchesCoin(UpbitMarketDto m, String lowerQ) {
        if (m.getMarket() != null && m.getMarket().toLowerCase(Locale.ROOT).contains(lowerQ)) return true;
        if (m.getKoreanName() != null && m.getKoreanName().contains(lowerQ)) return true;
        if (m.getEnglishName() != null && m.getEnglishName().toLowerCase(Locale.ROOT).contains(lowerQ)) return true;
        return false;
    }
}
