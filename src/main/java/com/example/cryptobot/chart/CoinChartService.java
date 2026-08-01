package com.example.cryptobot.chart;

import com.example.cryptobot.alert.AlertEvaluationService;
import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.market.candle.CandleRepository;
import com.example.cryptobot.strategy.hybrid.TechnicalIndicatorCalculator;
import com.example.cryptobot.strategy.indicator.Indicators;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 코인 차트 데이터 서비스.
 * 캔들 조회 + 지표 계산 통합 제공.
 */
@Service
@RequiredArgsConstructor
public class CoinChartService {

    private final CandleRepository candleRepository;
    private final AlertEvaluationService alertEvaluationService;

    /**
     * 특정 심볼의 캔들 목록을 조회합니다.
     * 오름차순(시간 순) 정렬.
     *
     * @param symbol 마켓 코드 (예: KRW-BTC)
     * @param period 캔들 주기 (FIFTEEN_MIN / ONE_HOUR / FOUR_HOUR)
     * @param limit  최대 캔들 수 (기본 100)
     * @return 캔들 목록 (최신순 DB 조회 후 오름차순 반환)
     */
    public List<CandleDto> getCandles(String symbol, String period, int limit) {
        String periodKey = resolvePeriodKey(period);
        List<Candle> candles = candleRepository
                .findTopNBySymbolAndPeriodOrderByTimestampDesc(symbol, periodKey, limit);

        if (candles == null || candles.isEmpty()) return List.of();

        candles.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

        List<Double> closes = candles.stream()
                .map(c -> c.getClosePrice() != null ? c.getClosePrice().doubleValue() : 0.0)
                .toList();

        // 이동평균 시리즈 (전체 캔들 길이 기준)
        List<Double> ema12s  = TechnicalIndicatorCalculator.calculateEMASeries(closes, 12);
        List<Double> ema26s  = TechnicalIndicatorCalculator.calculateEMASeries(closes, 26);
        TechnicalIndicatorCalculator.BollingerBands bb =
                closes.size() >= 20 ? TechnicalIndicatorCalculator.calculateBollingerBands(closes, 20) : null;

        int n = candles.size();
        return candles.stream().map(c -> {
            int idx = candles.indexOf(c);
            return CandleDto.from(c,
                    idx < ema12s.size() ? ema12s.get(idx) : null,
                    idx < ema26s.size() ? ema26s.get(idx) : null,
                    idx == n - 1 && bb != null ? bb.getUpper() : null,
                    idx == n - 1 && bb != null ? bb.getLower() : null,
                    idx == n - 1 && bb != null ? bb.getMiddle() : null);
        }).toList();
    }

    /**
     * 현재 지표 스냅샷 (단일 값 맵) 반환.
     */
    public Map<String, Double> getIndicators(String symbol) {
        return alertEvaluationService.getIndicatorSnapshot(symbol);
    }

    private String resolvePeriodKey(String period) {
        return switch (period.toUpperCase()) {
            case "1H", "ONE_HOUR"     -> Candle.CandlePeriod.ONE_HOUR.name();
            case "4H", "FOUR_HOUR"    -> Candle.CandlePeriod.FOUR_HOUR.name();
            default                   -> Candle.CandlePeriod.FIFTEEN_MIN.name();
        };
    }
}
