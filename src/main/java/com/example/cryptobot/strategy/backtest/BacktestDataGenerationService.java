package com.example.cryptobot.strategy.backtest;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.account.AccountService;
import com.example.cryptobot.exchange.upbit.client.UpbitApiClient;
import com.example.cryptobot.exchange.upbit.dto.UpbitCandleDto;
import com.example.cryptobot.exchange.upbit.service.UpbitMarketService;
import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.market.candle.CandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 백테스트 기반 ML 학습 데이터 대량 생성 서비스.
 *
 * <p>업비트 공개 캔들 API(API 키 불필요)로 과거 캔들을 수집한 후
 * {@link HybridBacktestEngine}을 실행하여 strategy_run_logs와 trade_history를
 * source='BACKTEST'로 채운다.
 *
 * <p>완료 기준:
 * <pre>
 * SELECT COUNT(*) FROM strategy_run_logs s
 * JOIN trade_history t ON t.signal_id = s.signal_id
 * WHERE s.source = 'BACKTEST'
 * </pre>
 * 가 수천 이상이면 Python train.py 실행 가능.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestDataGenerationService {

    private final UpbitApiClient upbitApiClient;
    private final UpbitMarketService upbitMarketService;
    private final CandleRepository candleRepository;
    private final HybridBacktestEngine hybridBacktestEngine;
    private final AccountService accountService;

    private static final int CANDLE_UNIT      = 15;       // 15분봉
    private static final String TIMEFRAME      = "15분";
    private static final int BATCH_SIZE        = 200;      // 업비트 API 최대
    private static final int RATE_LIMIT_MS     = 120;      // 업비트 Rate Limit 여유 (초당 ~8회)
    private static final DateTimeFormatter UTC_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /**
     * 대량 학습 데이터 생성 실행.
     *
     * @param symbols     분석 대상 심볼 목록 (예: ["KRW-BTC", "KRW-ETH", ...])
     * @param monthsBack  현재 시각 기준 과거 몇 개월치 데이터 수집 (예: 24)
     * @return 생성 결과 요약
     */
    public GenerationResult generate(List<String> symbols, int monthsBack) {
        Account account;
        try {
            account = accountService.getPrimaryAccount();
        } catch (Exception e) {
            log.warn("[BacktestGen] 계정 조회 실패 — trade_history 저장 건너뜀: {}", e.getMessage());
            account = null;
        }

        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusMonths(monthsBack);
        int totalSignals  = 0;
        int totalTrades   = 0;
        int failedSymbols = 0;

        for (String symbol : symbols) {
            try {
                List<Candle> candles = fetchHistoricalCandles(symbol, since);
                if (candles.size() < 60) {
                    log.warn("[BacktestGen] 캔들 부족: {} — {}개", symbol, candles.size());
                    continue;
                }
                log.info("[BacktestGen] {} — {}개 캔들 로드 완료, 백테스트 시작", symbol, candles.size());

                BacktestResult result = hybridBacktestEngine.run(symbol, candles, TIMEFRAME, account);
                totalSignals += result.totalTrades();
                totalTrades  += result.totalTrades();
                log.info("[BacktestGen] {} 완료 — 거래 {}건, 승률 {:.1f}%, 수익률 {:.2f}%",
                        symbol, result.totalTrades(),
                        result.winRate() * 100, result.totalReturn() * 100);
            } catch (Exception e) {
                log.error("[BacktestGen] {} 처리 실패", symbol, e);
                failedSymbols++;
            }
        }

        log.info("[BacktestGen] 전체 완료 — 심볼 {}개, 신호 {}건, 실패 {}개",
                symbols.size(), totalSignals, failedSymbols);
        return new GenerationResult(symbols.size() - failedSymbols, totalTrades, failedSymbols);
    }

    /**
     * 거래대금 상위 KRW 마켓 목록 조회 (업비트 공개 API).
     *
     * @param topN 상위 N개
     */
    public List<String> fetchTopKrwMarkets(int topN) {
        List<String> markets = upbitApiClient.getAllKrwMarkets();
        // BTC/ETH/XRP 등 주요 코인을 앞에 두기 위해 정렬은 거래대금 기반이 이상적이나,
        // /v1/market/all은 거래대금을 제공하지 않으므로 알파벳 순서로 반환 후 상위 N개 선택.
        return markets.stream().limit(topN).toList();
    }

    // ============================================================
    // 과거 캔들 수집 (페이지네이션)
    // ============================================================

    /**
     * 업비트 공개 API에서 since 이후의 15분봉 캔들을 페이지네이션으로 수집하여 DB에 저장하고 반환한다.
     *
     * <p>업비트 API는 내림차순(최신 먼저)으로 반환한다.
     * 수집 후 오름차순으로 정렬하여 백테스트에 넘긴다.
     */
    private List<Candle> fetchHistoricalCandles(String symbol, LocalDateTime since) {
        List<Candle> all = new ArrayList<>();
        String cursor = null; // null = 최신 봉부터

        while (true) {
            List<UpbitCandleDto> batch = upbitApiClient.getCandles(symbol, CANDLE_UNIT, BATCH_SIZE, cursor);
            if (batch.isEmpty()) break;

            // 가장 오래된 봉 확인
            UpbitCandleDto oldest = batch.get(batch.size() - 1);
            LocalDateTime oldestTime = parseUtcDateTime(oldest.getCandleDateTimeUtc());

            // DB 저장 및 Candle 변환
            List<Candle> saved = upbitMarketService.getAndSaveCandles(symbol, CANDLE_UNIT, BATCH_SIZE);
            // getAndSaveCandles는 최신 N개만 저장하므로, 과거 데이터는 직접 변환
            List<Candle> converted = convertDtos(batch, symbol);
            all.addAll(converted);

            // since 이전이면 중단
            if (oldestTime != null && !oldestTime.isAfter(since)) break;
            if (batch.size() < BATCH_SIZE) break; // 더 이상 데이터 없음

            // 다음 페이지: 가장 오래된 봉의 직전 시각으로 cursor 설정
            cursor = oldestTime != null ? oldestTime.format(UTC_FMT) : null;
            if (cursor == null) break;

            // Rate Limit 준수
            try { Thread.sleep(RATE_LIMIT_MS); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // since 이전 데이터 제거 + 오름차순 정렬
        all.removeIf(c -> c.getTimestamp() != null && c.getTimestamp().isBefore(since));
        all.sort((a, b) -> {
            if (a.getTimestamp() == null) return -1;
            if (b.getTimestamp() == null) return 1;
            return a.getTimestamp().compareTo(b.getTimestamp());
        });

        return all;
    }

    private List<Candle> convertDtos(List<UpbitCandleDto> dtos, String symbol) {
        List<Candle> result = new ArrayList<>();
        for (UpbitCandleDto dto : dtos) {
            LocalDateTime ts = parseUtcDateTime(dto.getCandleDateTimeUtc());
            if (ts == null) continue;

            // DB에서 기존 캔들 조회 (upsert)
            Candle candle = candleRepository
                    .findBySymbolAndPeriodAndTimestamp(symbol, Candle.CandlePeriod.FIFTEEN_MIN, ts)
                    .orElseGet(Candle::new);
            candle.setSymbol(symbol);
            candle.setPeriod(Candle.CandlePeriod.FIFTEEN_MIN);
            candle.setTimestamp(ts);
            candle.setOpenPrice(dto.getOpeningPrice());
            candle.setHighPrice(dto.getHighPrice());
            candle.setLowPrice(dto.getLowPrice());
            candle.setClosePrice(dto.getTradePrice());
            candle.setVolume(dto.getCandleAccTradeVolume());
            candle.setQuoteAssetVolume(dto.getCandleAccTradePrice());
            result.add(candle);
        }
        return result;
    }

    private static LocalDateTime parseUtcDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            try { return LocalDateTime.parse(s); } catch (Exception e2) { return null; }
        }
    }

    public record GenerationResult(int successSymbols, int totalTrades, int failedSymbols) {}
}
