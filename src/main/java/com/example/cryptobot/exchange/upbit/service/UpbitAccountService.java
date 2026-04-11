package com.example.cryptobot.exchange.upbit.service;

import com.example.cryptobot.exchange.upbit.client.UpbitApiClient;
import com.example.cryptobot.exchange.upbit.dto.UpbitAccountDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UpbitAccountService {

    private final UpbitApiClient upbitApiClient;

    public List<UpbitAccountDto> getAccounts() {
        try {
            List<UpbitAccountDto> accounts = upbitApiClient.getAccounts();
            log.debug("계정 조회 성공: {} 개 화폐", accounts.size());
            return accounts;
        } catch (Exception e) {
            log.error("계정 조회 실패", e);
            return List.of();
        }
    }

    public Optional<UpbitAccountDto> getBalance(String currency) {
        try {
            List<UpbitAccountDto> accounts = upbitApiClient.getAccounts();
            return accounts.stream()
                    .filter(acc -> acc.getCurrency() != null && acc.getCurrency().equalsIgnoreCase(currency))
                    .findFirst();
        } catch (Exception e) {
            log.error("잔고 조회 실패: {}", currency, e);
            return Optional.empty();
        }
    }

    public BigDecimal getKRWBalance() {
        return getBalance("KRW")
                .map(acc -> acc.getBalance() != null ? acc.getBalance() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal getAvailableKRW() {
        return getBalance("KRW")
                .map(acc -> {
                    BigDecimal balance = acc.getBalance() != null ? acc.getBalance() : BigDecimal.ZERO;
                    BigDecimal locked = acc.getLocked() != null ? acc.getLocked() : BigDecimal.ZERO;
                    return balance.subtract(locked);
                })
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal getTotalBalance(String currency) {
        return getBalance(currency)
                .map(acc -> acc.getBalance() != null ? acc.getBalance() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal getAvailableBalance(String currency) {
        return getBalance(currency)
                .map(acc -> {
                    BigDecimal balance = acc.getBalance() != null ? acc.getBalance() : BigDecimal.ZERO;
                    BigDecimal locked = acc.getLocked() != null ? acc.getLocked() : BigDecimal.ZERO;
                    return balance.subtract(locked);
                })
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal getTotalValuation() {
        try {
            List<UpbitAccountDto> accounts = upbitApiClient.getAccounts();
            return accounts.stream()
                    .map(acc -> acc.getBalance() != null ? acc.getBalance() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            log.error("총 평가액 계산 실패", e);
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal getAverageBuyPrice(String currency) {
        return getBalance(currency)
                .map(acc -> acc.getAvgBuyPrice() != null ? acc.getAvgBuyPrice() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }
}