package com.example.cryptobot.market;

import com.example.cryptobot.exchange.upbit.service.UpbitMarketService;
import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.market.ticker.Ticker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 업비트 공개 시세·캔들 조회 API (비인증, 프론트엔드 연동용).
 * 조회 결과는 DB에 저장됩니다.
 */
@RestController
@RequestMapping("/market")
@RequiredArgsConstructor
public class MarketController {

    private final UpbitMarketService upbitMarketService;

    /**
     * 단일 종목 현재가 조회.
     * 예: GET /api/market/ticker?market=KRW-BTC
     */
    @GetMapping("/ticker")
    public ResponseEntity<Ticker> getTicker(@RequestParam String market) {
        return ResponseEntity.ok(upbitMarketService.getAndSaveTicker(market));
    }

    /**
     * 다중 종목 현재가 조회 (쉼표 구분).
     * 예: GET /api/market/tickers?markets=KRW-BTC,KRW-ETH
     */
    @GetMapping("/tickers")
    public ResponseEntity<List<Ticker>> getTickers(@RequestParam String markets) {
        return ResponseEntity.ok(upbitMarketService.getAndSaveTickers(markets));
    }

    /**
     * 분봉 캔들 조회 (업비트 API 호출 후 DB 저장).
     * 예: GET /api/market/candles?market=KRW-BTC&unit=15&count=50
     *
     * @param unit  분 단위 (1, 3, 5, 15, 30, 60, 240)
     * @param count 조회 건수 (최대 200)
     */
    @GetMapping("/candles")
    public ResponseEntity<List<Candle>> getCandles(
            @RequestParam String market,
            @RequestParam int unit,
            @RequestParam(defaultValue = "100") int count) {
        return ResponseEntity.ok(upbitMarketService.getAndSaveCandles(market, unit, count));
    }
}
