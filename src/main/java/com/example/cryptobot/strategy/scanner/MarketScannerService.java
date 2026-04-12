package com.example.cryptobot.strategy.scanner;

import com.example.cryptobot.exchange.upbit.client.UpbitApiClient;
import com.example.cryptobot.exchange.upbit.dto.UpbitTickerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 전체 KRW 마켓을 스캔해서 매매 후보 코인을 선별하는 서비스.
 *
 * <h3>선별 기준</h3>
 * <ol>
 *   <li><b>유동성 필터</b>: 24h 거래대금 {@code minTradePrice24h} 이상</li>
 *   <li><b>변동률 필터</b>: 24h 변동률 {@code minChangeRate} ~ {@code maxChangeRate}</li>
 *   <li><b>경고 코인 제외</b>: marketWarning = CAUTION 제외</li>
 *   <li><b>거래 중단 제외</b>: isTradingSuspended = true 제외</li>
 * </ol>
 *
 * <h3>점수 산출 (100점 만점)</h3>
 * <ul>
 *   <li>거래대금 점수 (40점): 후보군 내 정규화된 24h 거래대금 순위</li>
 *   <li>모멘텀 점수 (30점): 변동률 기반 (0%~+15% 구간에서 선형 증가)</li>
 *   <li>52주 위치 점수 (30점): 52주 고가 대비 현재가 낮을수록 상승 여력 높음</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketScannerService {

    private final UpbitApiClient upbitApiClient;

    /** 24h 최소 거래대금 (기본 50억 KRW) */
    @Value("${trading.scanner.min-trade-price-24h:5000000000}")
    private long minTradePrice24h;

    /** 스캔 결과 최대 코인 수 */
    @Value("${trading.scanner.max-coins:20}")
    private int maxCoins;

    /** 24h 최소 변동률 (기본 -3%) */
    @Value("${trading.scanner.min-change-rate:-0.03}")
    private double minChangeRate;

    /** 24h 최대 변동률 (기본 +15%, 과매수 제외) */
    @Value("${trading.scanner.max-change-rate:0.15}")
    private double maxChangeRate;

    /**
     * 전체 KRW 마켓 스캔 후 상위 코인 목록 반환.
     *
     * @return 점수 순으로 정렬된 심볼 목록 (예: ["KRW-BTC", "KRW-ETH", ...])
     */
    public List<String> scanTopCoins() {
        // 1. 전체 KRW 마켓 목록 조회
        List<String> allMarkets = upbitApiClient.getAllKrwMarkets();
        if (allMarkets.isEmpty()) {
            log.warn("[Scanner] KRW 마켓 목록 조회 실패");
            return List.of();
        }
        log.info("[Scanner] 전체 KRW 마켓: {} 개", allMarkets.size());

        // 2. 배치로 티커 조회 (업비트 최대 100개)
        List<UpbitTickerDto> tickers = fetchAllTickers(allMarkets);

        // 3. 필터링
        List<UpbitTickerDto> candidates = tickers.stream()
                .filter(this::passesHardFilters)
                .toList();
        log.info("[Scanner] 필터 통과: {} 개", candidates.size());

        if (candidates.isEmpty()) return List.of();

        // 4. 정규화 점수 계산
        double maxVolume = candidates.stream()
                .mapToDouble(t -> t.getAccTradePrice24h().doubleValue())
                .max()
                .orElse(1.0);

        List<ScoredMarket> scored = candidates.stream()
                .map(t -> new ScoredMarket(t.getMarket(), computeScore(t, maxVolume)))
                .sorted(Comparator.comparingDouble(ScoredMarket::score).reversed())
                .limit(maxCoins)
                .toList();

        List<String> result = scored.stream().map(ScoredMarket::symbol).toList();
        log.info("[Scanner] 최종 선별 {} 개: {}", result.size(), result);
        return result;
    }

    // ---- 필터 ----

    private boolean passesHardFilters(UpbitTickerDto t) {
        // 거래대금 필터
        if (t.getAccTradePrice24h() == null ||
                t.getAccTradePrice24h().compareTo(BigDecimal.valueOf(minTradePrice24h)) < 0) {
            return false;
        }
        // 변동률 필터
        if (t.getSignedChangeRate() == null) return false;
        double rate = t.getSignedChangeRate().doubleValue();
        if (rate < minChangeRate || rate > maxChangeRate) return false;

        // 거래 중단 코인 제외
        if (Boolean.TRUE.equals(t.getIsTradingSuspended())) return false;

        // 투자 경고 코인 제외
        if ("CAUTION".equals(t.getMarketWarning())) return false;

        return true;
    }

    // ---- 점수 계산 ----

    /**
     * 코인 점수 계산 (100점 만점).
     *
     * @param t         티커 데이터
     * @param maxVolume 후보군 내 최대 거래대금 (정규화 기준)
     */
    private double computeScore(UpbitTickerDto t, double maxVolume) {
        // 거래대금 점수 (0 ~ 40점): 후보군 내 정규화
        double volumeScore = (t.getAccTradePrice24h().doubleValue() / maxVolume) * 40.0;

        // 모멘텀 점수 (0 ~ 30점): 0%~15% 구간 선형 증가
        double rate = t.getSignedChangeRate().doubleValue();
        double momentumScore;
        if (rate <= 0) {
            momentumScore = 0;
        } else {
            momentumScore = Math.min(30.0, (rate / 0.15) * 30.0);
        }

        // 52주 위치 점수 (0 ~ 30점): 현재가가 52주 고가 대비 낮을수록 상승 여력 높음
        double positionScore = 0;
        if (t.getHighest52WeekPrice() != null && t.getLowest52WeekPrice() != null
                && t.getTradePrice() != null) {
            double high52 = t.getHighest52WeekPrice().doubleValue();
            double low52 = t.getLowest52WeekPrice().doubleValue();
            double current = t.getTradePrice().doubleValue();
            double range = high52 - low52;
            if (range > 0) {
                double positionRatio = (current - low52) / range; // 0=저점, 1=고점
                positionScore = (1.0 - positionRatio) * 30.0;    // 저점에 가까울수록 고점
            }
        }

        return volumeScore + momentumScore + positionScore;
    }

    // ---- 유틸 ----

    private List<UpbitTickerDto> fetchAllTickers(List<String> markets) {
        List<UpbitTickerDto> all = new ArrayList<>();
        for (int i = 0; i < markets.size(); i += 100) {
            List<String> batch = markets.subList(i, Math.min(i + 100, markets.size()));
            String joined = String.join(",", batch);
            List<UpbitTickerDto> tickers = upbitApiClient.getTickers(joined);
            all.addAll(tickers);
        }
        return all;
    }

    private record ScoredMarket(String symbol, double score) {}
}
