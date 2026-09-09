package com.example.cryptobot.home;

import com.example.cryptobot.alert.UserAlert;
import com.example.cryptobot.alert.UserAlertService;
import com.example.cryptobot.auth.entity.User;
import com.example.cryptobot.auth.service.UserService;
import com.example.cryptobot.exchange.upbit.client.UpbitApiClient;
import com.example.cryptobot.exchange.upbit.dto.UpbitTickerDto;
import com.example.cryptobot.holding.HoldingService;
import com.example.cryptobot.holding.dto.HoldingResponse;
import com.example.cryptobot.home.dto.HomeResponse;
import com.example.cryptobot.news.NewsItem;
import com.example.cryptobot.news.NewsItemRepository;
import com.example.cryptobot.toss.TossSecuritiesApiClient;
import com.example.cryptobot.toss.TossSecuritiesApiClient.IndexPrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 홈 브리핑 집계 서비스.
 *
 * <p>코스피·코스닥(토스증권), BTC(업비트), 보유종목, 최근 알림, 뉴스를 집계하여 반환합니다.
 * 각 하위 API가 실패해도 나머지 항목은 정상 반환됩니다 (부분 응답 허용).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {

    private final UserService userService;
    private final TossSecuritiesApiClient tossApiClient;
    private final UpbitApiClient upbitApiClient;
    private final HoldingService holdingService;
    private final UserAlertService alertService;
    private final NewsItemRepository newsRepository;

    private static final String DISCLAIMER =
            "제공 정보는 참고용이며 투자 권유가 아닙니다. 코스피·코스닥은 토스증권 Open API 기준입니다.";

    /**
     * 홈 브리핑 데이터를 집계합니다.
     *
     * @param userId 요청 회원 ID
     * @return 홈 브리핑 응답
     */
    public HomeResponse buildHome(Long userId) {
        User user = userService.getActiveUser(userId);
        String greeting = "안녕하세요, " + user.getNickname() + "님!";

        // 지수 집계 — 각 API 실패는 null로 처리
        HomeResponse.IndicesSnapshot indices = fetchIndices();

        // 보유 종목 (현재가·미실현 손익 포함)
        List<HoldingResponse> holdings = safeGet(() -> holdingService.findAllHoldings(userId), List.of());

        // 최근 알림 5건 (최신순)
        List<UserAlert> recentAlerts = safeGet(
                () -> alertService.findByUserId(userId).stream().limit(5).toList(),
                List.of()
        );

        // 최신 뉴스 5건
        List<NewsItem> recentNews = safeGet(newsRepository::findTop5ByOrderByPublishedAtDesc, List.of());

        return new HomeResponse(greeting, indices, holdings, recentAlerts, recentNews, DISCLAIMER);
    }

    /**
     * 코스피·코스닥(토스증권) + BTC(업비트) 지수를 조회합니다.
     */
    private HomeResponse.IndicesSnapshot fetchIndices() {
        // 코스피·코스닥
        Map<String, IndexPrice> tossMap = tossApiClient.fetchIndices().stream()
                .collect(Collectors.toMap(IndexPrice::symbol, p -> p));

        HomeResponse.IndexEntry kospi  = toIndexEntry(tossMap.get("KOSPI"));
        HomeResponse.IndexEntry kosdaq = toIndexEntry(tossMap.get("KOSDAQ"));

        // BTC
        HomeResponse.IndexEntry btc = fetchBtcEntry();

        return new HomeResponse.IndicesSnapshot(kospi, kosdaq, btc);
    }

    private HomeResponse.IndexEntry toIndexEntry(IndexPrice price) {
        if (price == null) return null;
        return new HomeResponse.IndexEntry(price.lastPrice(), price.changeRate());
    }

    /**
     * 업비트에서 BTC 현재가와 등락률을 조회합니다.
     */
    private HomeResponse.IndexEntry fetchBtcEntry() {
        try {
            UpbitTickerDto ticker = upbitApiClient.getTicker("KRW-BTC");
            if (ticker == null) return null;
            double price      = ticker.getTradePrice() != null ? ticker.getTradePrice().doubleValue() : 0;
            double changeRate = ticker.getSignedChangeRate() != null
                    ? ticker.getSignedChangeRate().doubleValue() * 100 : 0;
            return new HomeResponse.IndexEntry(price, changeRate);
        } catch (Exception e) {
            log.warn("BTC 시세 조회 실패", e);
            return null;
        }
    }

    /**
     * 하위 API 실패 시 fallback 값을 반환하는 헬퍼.
     */
    private <T> T safeGet(java.util.function.Supplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("홈 브리핑 서브 항목 조회 실패: {}", e.getMessage());
            return fallback;
        }
    }
}
