package com.example.cryptobot.upbit.controller;

import com.example.cryptobot.upbit.dto.UpbitAccountDto;
import com.example.cryptobot.upbit.dto.UpbitOrderChanceDto;
import com.example.cryptobot.upbit.dto.UpbitOrderDto;
import com.example.cryptobot.upbit.service.UpbitAccountService;
import com.example.cryptobot.upbit.service.UpbitMarketService;
import com.example.cryptobot.upbit.service.UpbitOrderService;
import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.market.ticker.Ticker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/upbit")
@RequiredArgsConstructor
public class UpbitController {

    private final UpbitMarketService upbitMarketService;
    private final UpbitAccountService upbitAccountService;
    private final UpbitOrderService upbitOrderService;

    /**
     * 단일 시세 조회 및 저장
     * 예: GET /api/upbit/ticker?market=KRW-BTC
     */
    @GetMapping("/ticker")
    public ResponseEntity<Ticker> getTicker(@RequestParam String market) {
        Ticker ticker = upbitMarketService.getAndSaveTicker(market);
        return ResponseEntity.ok(ticker);
    }

    /**
     * 다중 시세 조회 및 저장
     * 예: GET /api/upbit/tickers?markets=KRW-BTC,KRW-ETH
     */
    @GetMapping("/tickers")
    public ResponseEntity<List<Ticker>> getTickers(@RequestParam String markets) {
        List<Ticker> tickers = upbitMarketService.getAndSaveTickers(markets);
        return ResponseEntity.ok(tickers);
    }

    /**
     * 캔들 조회 및 저장
     * 예: GET /api/upbit/candles?market=KRW-BTC&unit=1&count=10
     */
    @GetMapping("/candles")
    public ResponseEntity<List<Candle>> getCandles(
            @RequestParam String market,
            @RequestParam int unit,
            @RequestParam int count
    ) {
        List<Candle> candles = upbitMarketService.getAndSaveCandles(market, unit, count);
        return ResponseEntity.ok(candles);
    }

    /**
     * 전체 계정 조회
     * 예: GET /api/upbit/accounts
     */
    @GetMapping("/accounts")
    public ResponseEntity<List<UpbitAccountDto>> getAccounts() {
        List<UpbitAccountDto> accounts = upbitAccountService.getAccounts();
        return ResponseEntity.ok(accounts);
    }

    /**
     * 특정 화폐 잔고 조회
     * 예: GET /api/upbit/accounts/balance?currency=KRW
     */
    @GetMapping("/accounts/balance")
    public ResponseEntity<UpbitAccountDto> getBalance(@RequestParam String currency) {
        return upbitAccountService.getBalance(currency)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * KRW 사용 가능 잔액 조회
     * 예: GET /api/upbit/accounts/available-krw
     */
    @GetMapping("/accounts/available-krw")
    public ResponseEntity<BigDecimal> getAvailableKrw() {
        return ResponseEntity.ok(upbitAccountService.getAvailableKRW());
    }

    /**
     * 지정가 매수 주문
     * 예: POST /api/upbit/orders/buy
     */
    @PostMapping("/orders/buy")
    public ResponseEntity<UpbitOrderDto> placeBuyOrder(@RequestBody OrderRequest request) {
        UpbitOrderDto order = upbitOrderService.placeBuyOrder(
                request.getMarket(),
                request.getVolume(),
                request.getPrice()
        );
        return ResponseEntity.ok(order);
    }

    /**
     * 지정가 매도 주문
     * 예: POST /api/upbit/orders/sell
     */
    @PostMapping("/orders/sell")
    public ResponseEntity<UpbitOrderDto> placeSellOrder(@RequestBody OrderRequest request) {
        UpbitOrderDto order = upbitOrderService.placeSellOrder(
                request.getMarket(),
                request.getVolume(),
                request.getPrice()
        );
        return ResponseEntity.ok(order);
    }

    /**
     * 시장가 매수 주문
     * 예: POST /api/upbit/orders/buy-market
     */
    @PostMapping("/orders/buy-market")
    public ResponseEntity<UpbitOrderDto> placeBuyMarketOrder(@RequestBody MarketOrderRequest request) {
        UpbitOrderDto order = upbitOrderService.placeBuyMarketOrder(
                request.getMarket(),
                request.getVolume()
        );
        return ResponseEntity.ok(order);
    }

    /**
     * 시장가 매도 주문
     * 예: POST /api/upbit/orders/sell-market
     */
    @PostMapping("/orders/sell-market")
    public ResponseEntity<UpbitOrderDto> placeSellMarketOrder(@RequestBody MarketOrderRequest request) {
        UpbitOrderDto order = upbitOrderService.placeSellMarketOrder(
                request.getMarket(),
                request.getVolume()
        );
        return ResponseEntity.ok(order);
    }

    /**
     * 주문 취소
     * 예: DELETE /api/upbit/orders/{uuid}
     */
    @DeleteMapping("/orders/{uuid}")
    public ResponseEntity<UpbitOrderDto> cancelOrder(@PathVariable String uuid) {
        UpbitOrderDto order = upbitOrderService.cancelOrder(uuid);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/orders/chance")
    public ResponseEntity<UpbitOrderChanceDto> getOrderChance(@RequestParam String market) {
        UpbitOrderChanceDto result = upbitOrderService.getOrderChance(market);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/orders/test/buy")
    public ResponseEntity<UpbitOrderDto> testBuyOrder(@RequestBody OrderRequest request) {
        UpbitOrderDto result = upbitOrderService.testBuyOrder(
                request.getMarket(),
                request.getVolume(),
                request.getPrice()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/orders/test/sell")
    public ResponseEntity<UpbitOrderDto> testSellOrder(@RequestBody OrderRequest request) {
        UpbitOrderDto result = upbitOrderService.testSellOrder(
                request.getMarket(),
                request.getVolume(),
                request.getPrice()
        );
        return ResponseEntity.ok(result);
    }

    @Getter
    @Setter
    public static class OrderRequest {
        private String market;
        private BigDecimal volume;
        private BigDecimal price;
    }

    @Getter
    @Setter
    public static class MarketOrderRequest {
        private String market;
        private BigDecimal volume;
    }
}