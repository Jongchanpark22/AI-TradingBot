package com.example.cryptobot.account;

import com.example.cryptobot.common.exception.BusinessException;
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
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Account> getAccountsByUserId(Long userId) {
        return accountRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "계정을 찾을 수 없습니다."));
    }

    public Account createAccount(Long userId, String apiKey, String secretKey) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        Account account = Account.builder()
                .user(user)
                .apiKey(apiKey)
                .secretKey(secretKey)
                .isActive(false)
                .totalBalance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .build();

        return accountRepository.save(account);
    }

    public Account updateBalance(
            Long accountId,
            BigDecimal totalBalance,
            BigDecimal availableBalance,
            BigDecimal lockedBalance
    ) {
        Account account = getAccount(accountId);

        account.setTotalBalance(totalBalance != null ? totalBalance : BigDecimal.ZERO);
        account.setAvailableBalance(availableBalance != null ? availableBalance : BigDecimal.ZERO);
        account.setLockedBalance(lockedBalance != null ? lockedBalance : BigDecimal.ZERO);

        return accountRepository.save(account);
    }

    public void activateAccount(Long accountId) {
        Account account = getAccount(accountId);
        account.setIsActive(true);
        accountRepository.save(account);
        log.info("Account activated: {}", accountId);
    }

    public void deactivateAccount(Long accountId) {
        Account account = getAccount(accountId);
        account.setIsActive(false);
        accountRepository.save(account);
        log.info("Account deactivated: {}", accountId);
    }
}