package com.example.cryptobot.chart;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 코인 차트·지표 API.
 *
 * <p>React Native 앱에서 차트를 렌더링하고 지표 오버레이를 표시하기 위한 데이터 제공 엔드포인트.</p>
 * <p>⚠️ 면책: 지표 데이터는 정보 제공 목적이며 투자 권유가 아닙니다.</p>
 */
@RestController
@RequestMapping("/coins")
@RequiredArgsConstructor
public class CoinChartController {

    private final CoinChartService coinChartService;

    /**
     * 캔들 데이터 조회 (지표 오버레이 포함).
     *
     * @param symbol 마켓 코드 (예: KRW-BTC)
     * @param period 15m / 1H / 4H (기본 15m)
     * @param limit  캔들 수 (기본 100, 최대 200)
     */
    @GetMapping("/{symbol}/candles")
    public List<CandleDto> getCandles(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "15m") String period,
            @RequestParam(defaultValue = "100") int limit) {

        int safeLimit = Math.min(limit, 200);
        return coinChartService.getCandles(symbol, period, safeLimit);
    }

    /**
     * 현재 지표 스냅샷 조회.
     * RSI, EMA12, EMA26, SMA50, MACD, 볼린저밴드, ATR 등 단일값.
     *
     * @param symbol 마켓 코드 (예: KRW-BTC)
     */
    @GetMapping("/{symbol}/indicators")
    public Map<String, Double> getIndicators(@PathVariable String symbol) {
        return coinChartService.getIndicators(symbol);
    }
}
