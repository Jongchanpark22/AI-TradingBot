package com.example.cryptobot.holding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserHoldingRepository extends JpaRepository<UserHolding, Long> {

    List<UserHolding> findByUserIdOrderByCreatedAtDesc(Long userId);

    java.util.Optional<UserHolding> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndSymbol(Long userId, String symbol);
}
