package com.example.cryptobot.holding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    List<Watchlist> findAllByOrderByCreatedAtDesc();

    boolean existsBySymbol(String symbol);
}
