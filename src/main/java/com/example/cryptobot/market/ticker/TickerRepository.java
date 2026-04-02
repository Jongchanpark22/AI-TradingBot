package com.example.cryptobot.market.ticker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TickerRepository extends JpaRepository<Ticker, Long> {
    Optional<Ticker> findBySymbol(String symbol);
}

