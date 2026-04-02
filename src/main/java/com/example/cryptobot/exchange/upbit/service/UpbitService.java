package com.example.cryptobot.exchange.upbit.service;

import com.example.cryptobot.exchange.upbit.client.UpbitApiClient;
import com.example.cryptobot.exchange.upbit.dto.UpbitAccountDto;
import com.example.cryptobot.exchange.upbit.dto.UpbitCandleDto;
import com.example.cryptobot.exchange.upbit.dto.UpbitOrderDto;
import com.example.cryptobot.exchange.upbit.dto.UpbitTickerDto;
import com.example.cryptobot.market.ticker.Ticker;
import com.example.cryptobot.market.ticker.TickerRepository;
import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.market.candle.CandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 업비트 API 서비스
 * 
 * 주요 기능:
 * - 시세 데이터 조회 및 저장
 * - 캔들 데이터 조회 및 저장
 * - 주문 관련 기능
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UpbitService {

    private final UpbitApiClient upbitApiClient;
    private final TickerRepository tickerRepository;
    private final CandleRepository candleRepository;

    // ===== 시세 정보 =====

    /**
     * 시세 정보 조회 및 저장
     * 
     * @param market BTC-KRW, ETH-KRW 등
     */
    public Ticker getAndSaveTicker(String market) {
        try {
            UpbitTickerDto dto = upbitApiClient.getTicker(market);
            if (dto == null) {
                log.warn("시세 조회 실패: {}", market);
                return null;
            }

            Ticker ticker = Ticker.builder()
                    .symbol(market)
                    .currentPrice(dto.getTradePrice())
                    .highPrice24h(dto.getHighPrice())
                    .lowPrice24h(dto.getLowPrice())
                    .volume24h(dto.getAccTradeVolume24h())
                    .changePercent24h(dto.getSignedChangeRate())
                    .bid(dto.getTradePrice())
                    .ask(dto.getTradePrice())
                    .bidVolume(dto.getAccTradeVolume24h())
                    .askVolume(dto.getAccTradeVolume24h())
                    .marketCap(null)
                    .build();

            return tickerRepository.save(ticker);
        } catch (Exception e) {
            log.error("시세 저장 실패: {}", market, e);
            return null;
        }
    }

    /**
     * 여러 시세 정보 조회 및 저장
     */
    public List<Ticker> getAndSaveTickers(String markets) {
        try {
            List<UpbitTickerDto> dtos = upbitApiClient.getTickers(markets);
            if (dtos.isEmpty()) {
                log.warn("시세 조회 실패: {}", markets);
                return List.of();
            }

            List<Ticker> tickers = dtos.stream()
                    .map(dto -> Ticker.builder()
                            .symbol(dto.getMarket())
                            .currentPrice(dto.getTradePrice())
                            .highPrice24h(dto.getHighPrice())
                            .lowPrice24h(dto.getLowPrice())
                            .volume24h(dto.getAccTradeVolume24h())
                            .changePercent24h(dto.getSignedChangeRate())
                            .bid(dto.getTradePrice())
                            .ask(dto.getTradePrice())
                            .bidVolume(dto.getAccTradeVolume24h())
                            .askVolume(dto.getAccTradeVolume24h())
                            .marketCap(null)
                            .build())
                    .toList();

            return tickerRepository.saveAll(tickers);
        } catch (Exception e) {
            log.error("시세 저장 실패: {}", markets, e);
            return List.of();
        }
    }

    // ===== 캔들 데이터 =====

    /**
     * 캔들 데이터 조회 및 저장
     * 
     * @param market BTC-KRW, ETH-KRW 등
     * @param unit 분봉 단위 (1, 5, 15, 30, 60, 240)
     * @param count 조회 개수 (최대 200)
     */
    public List<Candle> getAndSaveCandles(String market, int unit, int count) {
        try {
            List<UpbitCandleDto> dtos = upbitApiClient.getCandles(market, unit, count);
            if (dtos.isEmpty()) {
                log.warn("캔들 조회 실패: {} {}분봉", market, unit);
                return List.of();
            }

            Candle.CandlePeriod period = convertToCandlePeriod(unit);

            List<Candle> candles = dtos.stream()
                    .map(dto -> Candle.builder()
                            .symbol(market)
                            .period(period)
                            .timestamp(parseDateTime(dto.getCandleDateTimeUtc()))
                            .openPrice(dto.getOpeningPrice())
                            .highPrice(dto.getHighPrice())
                            .lowPrice(dto.getLowPrice())
                            .closePrice(dto.getTradePrice())
                            .volume(dto.getCandleAccTradeVolume())
                            .quoteAssetVolume(dto.getCandleAccTradePrice())
                            .build())
                    .toList();

            return candleRepository.saveAll(candles);
        } catch (Exception e) {
            log.error("캔들 저장 실패: {} {}분봉", market, unit, e);
            return List.of();
        }
    }

    // ===== 주문 관련 =====

    /**
     * 매수 주문 생성
     */
    public UpbitOrderDto placeBuyOrder(String market, BigDecimal volume, BigDecimal price) {
        try {
            UpbitOrderDto order = upbitApiClient.createOrder(market, "bid", "limit", volume, price);
            if (order != null) {
                log.info("매수 주문 성공: {} {} @{}", market, volume, price);
            }
            return order;
        } catch (Exception e) {
            log.error("매수 주문 실패: {} {} @{}", market, volume, price, e);
            return null;
        }
    }

    /**
     * 매도 주문 생성
     */
    public UpbitOrderDto placeSellOrder(String market, BigDecimal volume, BigDecimal price) {
        try {
            UpbitOrderDto order = upbitApiClient.createOrder(market, "ask", "limit", volume, price);
            if (order != null) {
                log.info("매도 주문 성공: {} {} @{}", market, volume, price);
            }
            return order;
        } catch (Exception e) {
            log.error("매도 주문 실패: {} {} @{}", market, volume, price, e);
            return null;
        }
    }

    /**
     * 주문 취소
     */
    public UpbitOrderDto cancelOrder(String uuid) {
        try {
            UpbitOrderDto order = upbitApiClient.cancelOrder(uuid);
            if (order != null) {
                log.info("주문 취소 성공: {}", uuid);
            }
            return order;
        } catch (Exception e) {
            log.error("주문 취소 실패: {}", uuid, e);
            return null;
        }
    }

    // ===== 계정 정보 =====

    /**
     * 계정 잔액 조회
     */
    public List<UpbitAccountDto> getAccounts() {
        try {
            return upbitApiClient.getAccounts();
        } catch (Exception e) {
            log.error("계정 조회 실패", e);
            return List.of();
        }
    }

    // ===== 헬퍼 메서드 =====

    private Candle.CandlePeriod convertToCandlePeriod(int unit) {
        return switch (unit) {
            case 1 -> Candle.CandlePeriod.ONE_MIN;
            case 5 -> Candle.CandlePeriod.FIVE_MIN;
            case 15 -> Candle.CandlePeriod.FIFTEEN_MIN;
            case 30 -> Candle.CandlePeriod.THIRTY_MIN;
            case 60 -> Candle.CandlePeriod.ONE_HOUR;
            case 240 -> Candle.CandlePeriod.FOUR_HOUR;
            default -> Candle.CandlePeriod.ONE_HOUR;
        };
    }

    private LocalDateTime parseDateTime(String dateTimeString) {
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(dateTimeString);
            return zdt.toLocalDateTime();
        } catch (Exception e) {
            log.warn("날짜 파싱 실패: {}", dateTimeString);
            return LocalDateTime.now();
        }
    }
}

