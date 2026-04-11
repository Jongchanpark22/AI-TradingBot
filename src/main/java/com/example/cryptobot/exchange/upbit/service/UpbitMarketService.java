package com.example.cryptobot.exchange.upbit.service;

import com.example.cryptobot.exchange.upbit.client.UpbitApiClient;
import com.example.cryptobot.exchange.upbit.dto.UpbitCandleDto;
import com.example.cryptobot.exchange.upbit.dto.UpbitTickerDto;
import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.market.candle.CandleRepository;
import com.example.cryptobot.market.ticker.Ticker;
import com.example.cryptobot.market.ticker.TickerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UpbitMarketService {

    private final UpbitApiClient upbitApiClient;
    private final TickerRepository tickerRepository;
    private final CandleRepository candleRepository;

    public Ticker getAndSaveTicker(String market) {
        try {
            UpbitTickerDto dto = upbitApiClient.getTicker(market);
            if (dto == null) {
                log.warn("시세 조회 실패: {}", market);
                return null;
            }

            Ticker ticker = tickerRepository.findBySymbol(dto.getMarket())
                    .orElseGet(Ticker::new);

            ticker.setSymbol(dto.getMarket());
            ticker.setCurrentPrice(dto.getTradePrice());
            ticker.setHighPrice24h(dto.getHighPrice());
            ticker.setLowPrice24h(dto.getLowPrice());
            ticker.setVolume24h(dto.getAccTradeVolume24h());
            ticker.setChangePercent24h(dto.getSignedChangeRate());
            ticker.setBid(dto.getTradePrice());
            ticker.setAsk(dto.getTradePrice());
            ticker.setBidVolume(dto.getAccTradeVolume24h());
            ticker.setAskVolume(dto.getAccTradeVolume24h());
            ticker.setMarketCap(null);

            log.debug("시세 저장: {} = {}", market, dto.getTradePrice());
            return tickerRepository.save(ticker);
        } catch (Exception e) {
            log.error("시세 저장 실패: {}", market, e);
            return null;
        }
    }

    public List<Ticker> getAndSaveTickers(String markets) {
        try {
            List<UpbitTickerDto> dtos = upbitApiClient.getTickers(markets);
            if (dtos.isEmpty()) {
                log.warn("시세 조회 실패: {}", markets);
                return List.of();
            }

            List<Ticker> tickers = dtos.stream()
                    .map(dto -> {
                        Ticker ticker = tickerRepository.findBySymbol(dto.getMarket())
                                .orElseGet(Ticker::new);

                        ticker.setSymbol(dto.getMarket());
                        ticker.setCurrentPrice(dto.getTradePrice());
                        ticker.setHighPrice24h(dto.getHighPrice());
                        ticker.setLowPrice24h(dto.getLowPrice());
                        ticker.setVolume24h(dto.getAccTradeVolume24h());
                        ticker.setChangePercent24h(dto.getSignedChangeRate());
                        ticker.setBid(dto.getTradePrice());
                        ticker.setAsk(dto.getTradePrice());
                        ticker.setBidVolume(dto.getAccTradeVolume24h());
                        ticker.setAskVolume(dto.getAccTradeVolume24h());
                        ticker.setMarketCap(null);

                        return ticker;
                    })
                    .toList();

            log.debug("다중 시세 저장: {} 개", tickers.size());
            return tickerRepository.saveAll(tickers);
        } catch (Exception e) {
            log.error("다중 시세 저장 실패: {}", markets, e);
            return List.of();
        }
    }

    public List<Candle> getAndSaveCandles(String market, int unit, int count) {
        try {
            List<UpbitCandleDto> dtos = upbitApiClient.getCandles(market, unit, count);
            if (dtos.isEmpty()) {
                log.warn("캔들 조회 실패: {} {}분봉", market, unit);
                return List.of();
            }

            Candle.CandlePeriod period = convertToCandlePeriod(unit);

            List<Candle> candles = dtos.stream()
                    .map(dto -> {
                        LocalDateTime timestamp = parseDateTime(dto.getCandleDateTimeUtc());
                        if (timestamp == null) {
                            return null;
                        }

                        Optional<Candle> existing = candleRepository
                                .findBySymbolAndPeriodAndTimestamp(market, period, timestamp);

                        Candle candle = existing.orElseGet(Candle::new);
                        candle.setSymbol(market);
                        candle.setPeriod(period);
                        candle.setTimestamp(timestamp);
                        candle.setOpenPrice(dto.getOpeningPrice());
                        candle.setHighPrice(dto.getHighPrice());
                        candle.setLowPrice(dto.getLowPrice());
                        candle.setClosePrice(dto.getTradePrice());
                        candle.setVolume(dto.getCandleAccTradeVolume());
                        candle.setQuoteAssetVolume(dto.getCandleAccTradePrice());
                        return candle;
                    })
                    .filter(Objects::nonNull)
                    .toList();

            if (candles.isEmpty()) {
                log.warn("저장 가능한 캔들 없음: {} {}분봉", market, unit);
                return List.of();
            }

            log.debug("캔들 저장: {} {}분봉 {} 개", market, unit, candles.size());
            return candleRepository.saveAll(candles);
        } catch (Exception e) {
            log.error("캔들 저장 실패: {} {}분봉", market, unit, e);
            return List.of();
        }
    }

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
        if (dateTimeString == null || dateTimeString.isBlank()) {
            log.warn("캔들 시간값이 비어있음");
            return null;
        }

        try {
            return LocalDateTime.parse(dateTimeString);
        } catch (Exception ignored) {
        }

        try {
            return OffsetDateTime.parse(dateTimeString).toLocalDateTime();
        } catch (Exception ignored) {
        }

        try {
            return ZonedDateTime.parse(dateTimeString).toLocalDateTime();
        } catch (Exception ignored) {
        }

        log.warn("날짜 파싱 실패: {}", dateTimeString);
        return null;
    }
}