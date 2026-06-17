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
 * <h3>점수 산출 (100점 만점) — 유동성 + 트렌드 위치 + 모멘텀 우선</h3>
 * <ul>
 *   <li>거래대금 점수 (40점): 후보군 내 정규화된 24h 거래대금 순위 (유동성)</li>
 *   <li>트렌드 위치 점수 (35점): 24h 고저 범위 상위 55~85% 위치 코인 우대 (상승 추세 진행 중)</li>
 *   <li>모멘텀 점수 (25점): 변동률 +1%~+5% 구간 우대 (상승 추세 확인된 코인)</li>
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
     * 코인 점수 계산 (100점 만점) — 유동성 + 트렌드 위치 + 모멘텀 우선.
     *
     * <ul>
     *   <li>거래대금 점수 (40점): 유동성</li>
     *   <li>트렌드 위치 점수 (35점): 현재가가 24h 범위 55~85% 구간에 위치할수록 높음
     *       (상승 추세 진행 중 — 하락/보합 코인은 0점)</li>
     *   <li>모멘텀 점수 (25점): 변동률 +1%~+5% 구간 우대 (상승 추세 확인된 코인)</li>
     * </ul>
     */
    private double computeScore(UpbitTickerDto t, double maxVolume) {
        // 거래대금 점수 (0 ~ 40점): 후보군 내 정규화 — 유동성 높은 코인 우대
        double volumeScore = (t.getAccTradePrice24h().doubleValue() / maxVolume) * 40.0;

        // 트렌드 위치 점수 (0 ~ 35점): 24h 고저 범위의 상위 구간 코인 우대
        // position = 0 → 24h 최저가, 1 → 24h 최고가
        // 상승 추세 코인은 고점 근처에 위치 → 0.55~0.90 구간 최고점
        // 0.90 초과(극단 고점)는 단기 차익실현 위험 → 점수 감소
        double breakoutReadinessScore = 0;
        if (t.getHighPrice() != null && t.getLowPrice() != null && t.getTradePrice() != null) {
            double high24 = t.getHighPrice().doubleValue();
            double low24  = t.getLowPrice().doubleValue();
            double cur    = t.getTradePrice().doubleValue();
            double range  = high24 - low24;
            if (range > 0 && cur > 0) {
                double position = (cur - low24) / range;
                // 최적 구간: 0.55~0.85 (상위권, 상승 추세 진행 중)
                // 0.40~0.55: 중간권, 추세 전환 초입 → 부분 점수
                // 0.85~1.00: 극단 고점 → 선형 감소 (단기 조정 위험)
                // 0.40 미만: 하락 또는 보합 → 0점
                if (position >= 0.55 && position <= 0.85) {
                    breakoutReadinessScore = 35.0;
                } else if (position >= 0.40 && position < 0.55) {
                    breakoutReadinessScore = ((position - 0.40) / 0.15) * 20.0;
                } else if (position > 0.85 && position <= 1.0) {
                    breakoutReadinessScore = Math.max(0, (1.0 - position) / 0.15) * 35.0;
                }
                // position < 0.40 → 0점 (하락 추세)
            }
        }

        // 모멘텀 점수 (0 ~ 25점): +1%~+5% 구간 우대 (상승 추세 확인된 코인)
        // min-change-rate=0.005 필터 통과 코인 기준: 0%~+0.5%는 이미 차단됨
        double rate = t.getSignedChangeRate().doubleValue();
        double momentumScore;
        if (rate >= 0.01 && rate <= 0.05) {
            // +1%~+5%: 상승 모멘텀 최적 구간 (25점 만점)
            momentumScore = Math.max(0, (1.0 - Math.abs(rate - 0.03) / 0.02)) * 25.0;
        } else if (rate > 0.05 && rate <= 0.07) {
            // +5%~+7%: 강한 상승, 과매수 주의 → 낮은 점수
            momentumScore = Math.max(0, (0.07 - rate) / 0.02) * 12.0;
        } else if (rate >= 0.005 && rate < 0.01) {
            // +0.5%~+1%: 추세 초입, 소폭 점수
            momentumScore = Math.max(0, (rate - 0.005) / 0.005) * 10.0;
        } else {
            momentumScore = 0;
        }

        return volumeScore + breakoutReadinessScore + momentumScore;
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
