package com.example.cryptobot.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface RiskRecordRepository extends JpaRepository<RiskRecord, Long> {
    Optional<RiskRecord> findByAccountIdAndTradeDate(Long accountId, LocalDate tradeDate);
}

