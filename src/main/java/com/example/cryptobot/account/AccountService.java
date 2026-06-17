package com.example.cryptobot.account;

import com.example.cryptobot.common.exception.BusinessException;
import com.example.cryptobot.exchange.upbit.dto.UpbitAccountDto;
import com.example.cryptobot.exchange.upbit.service.UpbitAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final UpbitAccountService upbitAccountService;

    @Transactional(readOnly = true)
    public Account getPrimaryAccount() {
        return accountRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "기본 계정이 없습니다."));
    }

    public Account createDefaultAccountIfNotExists() {
        return accountRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> accountRepository.save(
                        Account.builder()
                                .isActive(false)
                                .totalBalance(BigDecimal.ZERO)
                                .availableBalance(BigDecimal.ZERO)
                                .lockedBalance(BigDecimal.ZERO)
                                .build()
                ));
    }

    public Account syncBalanceFromUpbit() {
        Account account = createDefaultAccountIfNotExists();

        List<UpbitAccountDto> holdings = upbitAccountService.getAccounts();

        BigDecimal krwBalance = BigDecimal.ZERO;
        BigDecimal availableKrw = BigDecimal.ZERO;
        BigDecimal coinValuation = BigDecimal.ZERO;

        for (UpbitAccountDto h : holdings) {
            if ("KRW".equalsIgnoreCase(h.getCurrency())) {
                krwBalance = h.getBalance() != null ? h.getBalance() : BigDecimal.ZERO;
                BigDecimal locked = h.getLocked() != null ? h.getLocked() : BigDecimal.ZERO;
                availableKrw = krwBalance.subtract(locked).max(BigDecimal.ZERO);
            } else {
                // 코인 평가금: 보유수량 * 평균매수가 (매입가 기준)
                if (h.getBalance() != null && h.getAvgBuyPrice() != null
                        && h.getBalance().compareTo(BigDecimal.ZERO) > 0
                        && h.getAvgBuyPrice().compareTo(BigDecimal.ZERO) > 0) {
                    coinValuation = coinValuation.add(h.getBalance().multiply(h.getAvgBuyPrice()));
                }
            }
        }

        // totalBalance = 원화 + 코인 평가금 (매입가 기준)
        BigDecimal totalBalance = krwBalance.add(coinValuation);
        BigDecimal lockedBalance = krwBalance.subtract(availableKrw);

        account.setTotalBalance(totalBalance);
        account.setAvailableBalance(availableKrw);
        account.setLockedBalance(lockedBalance.compareTo(BigDecimal.ZERO) >= 0 ? lockedBalance : BigDecimal.ZERO);

        log.info("[계정 싱크] 원화={}, 코인평가금={}, 총자산={}, 가용원화={}",
                krwBalance, coinValuation, totalBalance, availableKrw);

        return accountRepository.save(account);
    }

    public void activateAccount() {
        Account account = createDefaultAccountIfNotExists();
        account.setIsActive(true);
        accountRepository.save(account);
        log.info("Primary account activated");
    }

    public void deactivateAccount() {
        Account account = createDefaultAccountIfNotExists();
        account.setIsActive(false);
        accountRepository.save(account);
        log.info("Primary account deactivated");
    }
}