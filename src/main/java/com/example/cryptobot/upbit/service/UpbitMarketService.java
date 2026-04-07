package com.example.cryptobot.upbit.service;

import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.market.candle.CandleRepository;
import com.example.cryptobot.market.ticker.Ticker;
import com.example.cryptobot.market.ticker.TickerRepository;
import com.example.cryptobot.upbit.client.UpbitApiClient;
import com.example.cryptobot.upbit.dto.UpbitCandleDto;
import com.example.cryptobot.upbit.dto.UpbitTickerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UpbitMarketService {

    private final UpbitApiClient upbitApiClient;
    private final TickerRepository tickerRepository;
    private final CandleRepository candleRepository;

    @Transactional(readOnly = true)
    public UpbitTickerDto getTicker(String market) {
        try {
            UpbitTickerDto dto = upbitApiClient.getTicker(market);
            if (dto == null) {
                log.warn("시세 조회 실패: {}", market);
                return null;
            }
            return dto;
        } catch (Exception e) {
            log.error("시세 조회 예외: {}", market, e);
            return null;
        }
    }

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

            List<CandleSeed> seeds = dtos.stream()
                    .map(dto -> {
                        LocalDateTime timestamp = parseDateTime(dto.getCandleDateTimeUtc());
                        if (timestamp == null) {
                            return null;
                        }
                        return new CandleSeed(dto, timestamp);
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(CandleSeed::timestamp))
                    .toList();

            if (seeds.isEmpty()) {
                log.warn("파싱 가능한 캔들 없음: {} {}분봉", market, unit);
                return List.of();
            }

            List<LocalDateTime> timestamps = seeds.stream()
                    .map(CandleSeed::timestamp)
                    .distinct()
                    .toList();

            List<Candle> existingCandles =
                    candleRepository.findBySymbolAndPeriodAndTimestampIn(market, period, timestamps);

            Map<LocalDateTime, Candle> existingMap = existingCandles.stream()
                    .collect(Collectors.toMap(
                            Candle::getTimestamp,
                            Function.identity(),
                            (a, b) -> a
                    ));

            // 가장 최신 봉 1개만 update 허용
            LocalDateTime latestTimestamp = seeds.get(seeds.size() - 1).timestamp();

            List<Candle> candlesToSave = new ArrayList<>();
            int insertedCount = 0;
            int updatedCount = 0;
            int skippedCount = 0;

            for (CandleSeed seed : seeds) {
                UpbitCandleDto dto = seed.dto();
                LocalDateTime timestamp = seed.timestamp();

                Candle existing = existingMap.get(timestamp);

                // DB에 없으면 insert
                if (existing == null) {
                    Candle newCandle = Candle.builder()
                            .symbol(market)
                            .period(period)
                            .timestamp(timestamp)
                            .openPrice(dto.getOpeningPrice())
                            .highPrice(dto.getHighPrice())
                            .lowPrice(dto.getLowPrice())
                            .closePrice(dto.getTradePrice())
                            .volume(dto.getCandleAccTradeVolume())
                            .quoteAssetVolume(dto.getCandleAccTradePrice())
                            .build();

                    candlesToSave.add(newCandle);
                    insertedCount++;
                    continue;
                }

                // 기존 봉이면 최신 봉 1개만 update 허용
                if (!timestamp.equals(latestTimestamp)) {
                    skippedCount++;
                    continue;
                }

                boolean changed = false;

                if (!Objects.equals(existing.getOpenPrice(), dto.getOpeningPrice())) {
                    existing.setOpenPrice(dto.getOpeningPrice());
                    changed = true;
                }
                if (!Objects.equals(existing.getHighPrice(), dto.getHighPrice())) {
                    existing.setHighPrice(dto.getHighPrice());
                    changed = true;
                }
                if (!Objects.equals(existing.getLowPrice(), dto.getLowPrice())) {
                    existing.setLowPrice(dto.getLowPrice());
                    changed = true;
                }
                if (!Objects.equals(existing.getClosePrice(), dto.getTradePrice())) {
                    existing.setClosePrice(dto.getTradePrice());
                    changed = true;
                }
                if (!Objects.equals(existing.getVolume(), dto.getCandleAccTradeVolume())) {
                    existing.setVolume(dto.getCandleAccTradeVolume());
                    changed = true;
                }
                if (!Objects.equals(existing.getQuoteAssetVolume(), dto.getCandleAccTradePrice())) {
                    existing.setQuoteAssetVolume(dto.getCandleAccTradePrice());
                    changed = true;
                }

                if (changed) {
                    candlesToSave.add(existing);
                    updatedCount++;
                } else {
                    skippedCount++;
                }
            }

            if (candlesToSave.isEmpty()) {
                log.debug("캔들 변경 없음: {} {}분봉, existing={}, skipped={}",
                        market, unit, existingCandles.size(), skippedCount);
                return List.of();
            }

            List<Candle> saved = candleRepository.saveAll(candlesToSave);

            log.debug("캔들 저장 완료: {} {}분봉 saved={}, inserted={}, updated={}, skipped={}",
                    market, unit, saved.size(), insertedCount, updatedCount, skippedCount);

            return saved;

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

    private record CandleSeed(UpbitCandleDto dto, LocalDateTime timestamp) {
    }
}