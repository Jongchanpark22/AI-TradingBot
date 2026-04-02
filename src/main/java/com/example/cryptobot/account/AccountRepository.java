package com.example.cryptobot.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByUserId(Long userId);
    Optional<Account> findByUserIdAndExchangeType(Long userId, Account.ExchangeType exchangeType);
    List<Account> findByIsActive(Boolean isActive);
}

