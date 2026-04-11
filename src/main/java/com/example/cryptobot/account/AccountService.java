package com.example.cryptobot.account;

import com.example.cryptobot.common.exception.BusinessException;
import com.example.cryptobot.exchange.upbit.service.UpbitAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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

        BigDecimal totalBalance = upbitAccountService.getKRWBalance();
        BigDecimal availableBalance = upbitAccountService.getAvailableKRW();
        BigDecimal lockedBalance = totalBalance.subtract(availableBalance);

        account.setTotalBalance(totalBalance);
        account.setAvailableBalance(availableBalance);
        account.setLockedBalance(lockedBalance.compareTo(BigDecimal.ZERO) >= 0 ? lockedBalance : BigDecimal.ZERO);

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