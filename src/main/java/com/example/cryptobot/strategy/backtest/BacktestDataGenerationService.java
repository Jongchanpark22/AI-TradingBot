package com.example.cryptobot.strategy.backtest;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.account.AccountRepository;
import com.example.cryptobot.exchange.upbit.client.UpbitApiClient;
import com.example.cryptobot.strategy.core.StrategyRunLogRepository;
import com.example.cryptobot.exchange.upbit.dto.UpbitCandleDto;
import com.example.cryptobot.market.candle.Candle;
import com.example.cryptobot.market.candle.CandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 백테스트 기반 ML 학습 데이터 대량 생성 서비스.
 *
 * <p>업비트 공개 캔들 API(API 키 불필요)로 과거 캔들을 수집한 후
 * {@link HybridBacktestEngine}을 실행하여 strategy_run_logs와 trade_history를
 * source='BACKTEST'로 채운다.
 *
 * <p>최적화:
 * <ul>
 *   <li>캔들이 DB에 이미 있으면 API 호출 없이 DB에서 직접 로드</li>
 *   <li>캔들 저장은 개별 쿼리 대신 배치 saveAll()로 처리</li>
 *   <li>계정이 없으면 백테스트 전용 계정 자동 생성</li>
 *   <li>재실행 시 기존 BACKTEST 로그 정리 후 재생성</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestDataGenerationService {

    private final UpbitApiClient upbitApiClient;
    private final CandleRepository candleRepository;
    private final HybridBacktestEngine hybridBacktestEngine;
    private final AccountRepository accountRepository;
    private final StrategyRunLogRepository strategyRunLogRepository;

    private static final int CANDLE_UNIT   = 15;
    private static final String TIMEFRAME  = "15분";
    private static final int BATCH_SIZE    = 200;
    private static final int RATE_LIMIT_MS = 130;  // 업비트 Rate Limit (초당 ~7.5회)
    private static final DateTimeFormatter UTC_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /**
     * 대량 학습 데이터 생성 실행.
     *
     * @param symbols    분석 대상 심볼 목록
     * @param monthsBack 과거 몇 개월치 데이터 수집 (예: 24)
     */
    public GenerationResult generate(List<String> symbols, int monthsBack) {
        // 백테스트 전용 계정 확보 (없으면 자동 생성)
        Account account = getOrCreateBacktestAccount();

        // 기존 BACKTEST 신호 로그 삭제 (재실행 시 중복 방지)
        int deleted = strategyRunLogRepository.deleteBySource("BACKTEST");
        if (deleted > 0) {
            log.info("[BacktestGen] 기존 BACKTEST 로그 {}개 삭제 (재실행)", deleted);
        }

        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusMonths(monthsBack);
        int totalTrades = 0;
        int failedSymbols = 0;

        for (String symbol : symbols) {
            try {
                List<Candle> candles = fetchOrLoadCandles(symbol, since);
                if (candles.size() < 60) {
                    log.warn("[BacktestGen] 캔들 부족: {} — {}개 (최소 60 필요)", symbol, candles.size());
                    continue;
                }
                log.info("[BacktestGen] {} — {}개 캔들, 백테스트 시작", symbol, candles.size());

                BacktestResult result = hybridBacktestEngine.run(symbol, candles, TIMEFRAME, account);
                totalTrades += result.totalTrades();

                log.info("[BacktestGen] {} 완료 — 거래 {}건, 승률 {:.1f}%, 수익률 {:.2f}%",
                        symbol, result.totalTrades(),
                        result.winRate() * 100, result.totalReturn() * 100);
            } catch (Exception e) {
                log.error("[BacktestGen] {} 처리 실패", symbol, e);
                failedSymbols++;
            }
        }

        log.info("[BacktestGen] 전체 완료 — 심볼 {}개, 거래 {}건, 실패 {}개",
                symbols.size(), totalTrades, failedSymbols);
        return new GenerationResult(symbols.size() - failedSymbols, totalTrades, failedSymbols);
    }

    /**
     * 거래대금 상위 KRW 마켓 목록 조회.
     */
    public List<String> fetchTopKrwMarkets(int topN) {
        return upbitApiClient.getAllKrwMarkets().stream().limit(topN).toList();
    }

    // ============================================================
    // 캔들 로드 (DB 우선, 없으면 API)
    // ============================================================

    /**
     * 캔들 데이터를 DB에서 먼저 찾고, 부족하면 업비트 공개 API에서 수집하여 DB에 저장한다.
     */
    private List<Candle> fetchOrLoadCandles(String symbol, LocalDateTime since) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // DB에 충분한 캔들이 있으면 API 호출 없이 반환
        List<Candle> existing = candleRepository.findBySymbolAndPeriodAndTimestampBetween(
                symbol, Candle.CandlePeriod.FIFTEEN_MIN, since, now);
        long expectedBars = ChronoUnit.MINUTES.between(since, now) / 15;
        if (existing.size() >= expectedBars * 8 / 10) {  // 80% 이상 보유 시 DB 사용
            log.info("[BacktestGen] {} DB 캐시 사용 — {}개 캔들", symbol, existing.size());
            existing.sort(Comparator.comparing(Candle::getTimestamp));
            return existing;
        }

        // API에서 페이지네이션으로 수집
        log.info("[BacktestGen] {} API 수집 시작 (DB={}개, 예상={}개)", symbol, existing.size(), expectedBars);
        return fetchFromApi(symbol, since);
    }

    @Transactional
    public List<Candle> fetchFromApi(String symbol, LocalDateTime since) {
        List<Candle> all = new ArrayList<>();
        String cursor = null;

        while (true) {
            List<UpbitCandleDto> batch = upbitApiClient.getCandles(symbol, CANDLE_UNIT, BATCH_SIZE, cursor);
            if (batch.isEmpty()) break;

            UpbitCandleDto oldest = batch.get(batch.size() - 1);
            LocalDateTime oldestTime = parseUtcDateTime(oldest.getCandleDateTimeUtc());

            // DTO → Candle 변환 (개별 DB 조회 없이 새 객체 생성)
            List<Candle> converted = batch.stream()
                    .map(dto -> convertDto(dto, symbol))
                    .filter(c -> c != null)
                    .toList();
            all.addAll(converted);

            if (oldestTime != null && !oldestTime.isAfter(since)) break;
            if (batch.size() < BATCH_SIZE) break;

            cursor = oldestTime != null ? oldestTime.format(UTC_FMT) : null;
            if (cursor == null) break;

            try { Thread.sleep(RATE_LIMIT_MS); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // since 이전 제거 + 오름차순 정렬
        all.removeIf(c -> c.getTimestamp() != null && c.getTimestamp().isBefore(since));
        all.sort(Comparator.comparing(Candle::getTimestamp));

        // 배치 저장 (중복 무시: 이미 있는 timestamp는 insert 충돌 — 신규 수집이므로 중복 없음)
        if (!all.isEmpty()) {
            try {
                candleRepository.saveAll(all);
                log.info("[BacktestGen] {} 캔들 {}개 DB 저장 완료", symbol, all.size());
            } catch (Exception e) {
                log.warn("[BacktestGen] {} 캔들 저장 실패 (중복 또는 제약 위반), 인메모리로 진행: {}", symbol, e.getMessage());
            }
        }

        return all;
    }

    private Candle convertDto(UpbitCandleDto dto, String symbol) {
        LocalDateTime ts = parseUtcDateTime(dto.getCandleDateTimeUtc());
        if (ts == null) return null;

        Candle c = new Candle();
        c.setSymbol(symbol);
        c.setPeriod(Candle.CandlePeriod.FIFTEEN_MIN);
        c.setTimestamp(ts);
        c.setOpenPrice(dto.getOpeningPrice());
        c.setHighPrice(dto.getHighPrice());
        c.setLowPrice(dto.getLowPrice());
        c.setClosePrice(dto.getTradePrice());
        c.setVolume(dto.getCandleAccTradeVolume());
        c.setQuoteAssetVolume(dto.getCandleAccTradePrice());
        return c;
    }

    // ============================================================
    // 계정 관리
    // ============================================================

    /**
     * 백테스트 전용 계정 반환. 계정이 없으면 자동 생성한다.
     */
    @Transactional
    public Account getOrCreateBacktestAccount() {
        return accountRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    Account a = Account.builder()
                            .isActive(true)
                            .totalBalance(BigDecimal.valueOf(10_000_000))
                            .availableBalance(BigDecimal.valueOf(10_000_000))
                            .lockedBalance(BigDecimal.ZERO)
                            .build();
                    Account saved = accountRepository.save(a);
                    log.info("[BacktestGen] 백테스트 전용 계정 생성 (id={})", saved.getId());
                    return saved;
                });
    }

    // ============================================================
    // 유틸
    // ============================================================

    private static LocalDateTime parseUtcDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME); }
        catch (Exception e1) {
            try { return LocalDateTime.parse(s); }
            catch (Exception e2) { return null; }
        }
    }

    public record GenerationResult(int successSymbols, int totalTrades, int failedSymbols) {}
}
