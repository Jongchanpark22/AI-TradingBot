package com.example.cryptobot.market.candle;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CandleRepository extends JpaRepository<Candle, Long> {

    Optional<Candle> findBySymbolAndPeriodAndTimestamp(
            String symbol,
            Candle.CandlePeriod period,
            LocalDateTime timestamp
    );

    List<Candle> findBySymbolAndPeriodOrderByTimestampDesc(
            String symbol,
            Candle.CandlePeriod period,
            Pageable pageable
    );

    List<Candle> findBySymbolAndPeriodAndTimestampIn(
            String symbol,
            Candle.CandlePeriod period,
            Collection<LocalDateTime> timestamps
    );
}