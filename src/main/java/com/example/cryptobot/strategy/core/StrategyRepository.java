package com.example.cryptobot.strategy.core;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StrategyRepository extends JpaRepository<Strategy, Long> {
    List<Strategy> findByAccountId(Long accountId);
    List<Strategy> findByAccountIdAndStatus(Long accountId, Strategy.StrategyStatus status);
    List<Strategy> findByAccountIdAndSymbol(Long accountId, String symbol);
    
    // 활성 전략 조회
    List<Strategy> findByStatusAndAccount_IsActive(Strategy.StrategyStatus status, Boolean isActive);
}
