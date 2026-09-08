package com.example.cryptobot.holding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    List<Watchlist> findByUserIdOrderByCreatedAtDesc(Long userId);

    java.util.Optional<Watchlist> findByIdAndUserId(Long id, Long userId);
}
