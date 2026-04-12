package com.example.cryptobot.market.candle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CandleRepository extends JpaRepository<Candle, Long> {
    List<Candle> findBySymbolAndPeriodOrderByTimestampDesc(String symbol, Candle.CandlePeriod period);
    List<Candle> findBySymbolAndPeriodAndTimestampBetween(
            String symbol, Candle.CandlePeriod period, LocalDateTime startTime, LocalDateTime endTime);

    Optional<Candle> findBySymbolAndPeriodAndTimestamp(
            String symbol,
            Candle.CandlePeriod period,
            LocalDateTime timestamp
    );
    // 최근 N개 캔들 조회 — period는 DB 저장값(EnumType.STRING)과 일치하도록 String으로 받음
    @Query(value = "SELECT * FROM candles WHERE symbol = ?1 AND period = ?2 ORDER BY timestamp DESC LIMIT ?3", nativeQuery = true)
    List<Candle> findTopNBySymbolAndPeriodOrderByTimestampDesc(String symbol, String period, int limit);
}
