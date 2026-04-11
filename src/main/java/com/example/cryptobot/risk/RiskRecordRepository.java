package com.example.cryptobot.risk;

import com.example.cryptobot.account.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface RiskRecordRepository extends JpaRepository<RiskRecord, Long> {

    Optional<RiskRecord> findByAccountAndTradeDate(Account account, LocalDate tradeDate);
}