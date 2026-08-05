package com.example.cryptobot.account;

import com.example.cryptobot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;

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
                                .totalBalance(java.math.BigDecimal.ZERO)
                                .availableBalance(java.math.BigDecimal.ZERO)
                                .lockedBalance(java.math.BigDecimal.ZERO)
                                .build()
                ));
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
