package com.example.cryptobot.trade;

import com.example.cryptobot.account.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeHistoryService {

    private final TradeHistoryRepository repository;

    /**
     * 거래 완료 시 호출 — 손익을 계산하여 저장한다.
     *
     * @param account    거래 계정
     * @param symbol     심볼 (예: KRW-BTC)
     * @param entryPrice 평균 매수가
     * @param exitPrice  청산가
     * @param quantity   청산 수량
     * @param entryTime  포지션 진입 시각
     * @param exitType   청산 유형
     * @param exitReason 청산 상세 사유
     * @param atrAtEntry 진입 시 ATR
     * @param highestPrice 포지션 유지 중 최고가
     * @param partialExit 부분 청산 여부
     */
    @Transactional
    public TradeHistory record(
            Account account,
            String symbol,
            BigDecimal entryPrice,
            BigDecimal exitPrice,
            BigDecimal quantity,
            LocalDateTime entryTime,
            TradeHistory.ExitType exitType,
            String exitReason,
            Double atrAtEntry,
            Double highestPrice,
            boolean partialExit) {

        if (entryPrice == null || exitPrice == null || quantity == null
                || entryPrice.compareTo(BigDecimal.ZERO) <= 0
                || exitPrice.compareTo(BigDecimal.ZERO) <= 0
                || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[TradeHistory] 유효하지 않은 거래 데이터 — symbol={}", symbol);
            return null;
        }

        BigDecimal entryAmount = entryPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        BigDecimal exitAmount  = exitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitAmount = exitAmount.subtract(entryAmount);
        BigDecimal profitRate   = profitAmount
                .divide(entryAmount, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);

        TradeHistory history = TradeHistory.builder()
                .account(account)
                .symbol(symbol)
                .entryPrice(entryPrice)
                .exitPrice(exitPrice)
                .quantity(quantity)
                .entryAmount(entryAmount)
                .exitAmount(exitAmount)
                .profitAmount(profitAmount)
                .profitRate(profitRate)
                .entryTime(entryTime)
                .exitTime(LocalDateTime.now())
                .exitType(exitType)
                .exitReason(exitReason)
                .atrAtEntry(atrAtEntry)
                .highestPrice(highestPrice)
                .partialExit(partialExit)
                .build();

        TradeHistory saved = repository.save(history);

        String sign = profitAmount.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        log.info("📈 [거래완료] {} | 진입={} | 청산={} | 손익={}{}원 ({}{}%) | 사유={}",
                symbol,
                String.format("%,.0f", entryPrice),
                String.format("%,.0f", exitPrice),
                sign, String.format("%,.0f", profitAmount),
                sign, profitRate,
                exitReason);

        return saved;
    }

    // ---- 조회 ----

    @Transactional(readOnly = true)
    public List<TradeHistory> getAll(Long accountId) {
        return repository.findByAccountIdOrderByExitTimeDesc(accountId);
    }

    @Transactional(readOnly = true)
    public List<TradeHistory> getBySymbol(Long accountId, String symbol) {
        return repository.findByAccountIdAndSymbolOrderByExitTimeDesc(accountId, symbol);
    }

    @Transactional(readOnly = true)
    public List<TradeHistory> getByDateRange(Long accountId, LocalDateTime from, LocalDateTime to) {
        return repository.findByAccountIdAndExitTimeBetweenOrderByExitTimeDesc(accountId, from, to);
    }

    @Transactional(readOnly = true)
    public TradeStats getStats(Long accountId) {
        Object[] raw = repository.aggregateStats(accountId);
        long total       = raw[0] != null ? ((Number) raw[0]).longValue() : 0;
        BigDecimal totalProfit = raw[1] != null ? (BigDecimal) raw[1] : BigDecimal.ZERO;
        BigDecimal avgRate     = raw[2] != null ? (BigDecimal) raw[2] : BigDecimal.ZERO;

        BigDecimal todayProfit = repository.sumProfitToday(
                accountId, LocalDate.now().atStartOfDay());

        return new TradeStats(total, totalProfit, avgRate.setScale(4, RoundingMode.HALF_UP), todayProfit);
    }

    public record TradeStats(
            long totalTrades,
            BigDecimal totalProfit,
            BigDecimal avgProfitRate,
            BigDecimal todayProfit) {}
}
