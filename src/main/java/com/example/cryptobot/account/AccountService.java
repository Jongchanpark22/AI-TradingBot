package com.example.cryptobot.account;

import com.example.cryptobot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public List<Account> getAccountsByUserId(Long userId) {
        return accountRepository.findByUserId(userId);
    }

    public Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "계정을 찾을 수 없습니다."));
    }

    public Account createAccount(Long userId, Account.ExchangeType exchangeType, String apiKey, String secretKey) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        Account account = Account.builder()
                .user(user)
                .exchangeType(exchangeType)
                .apiKey(apiKey)
                .secretKey(secretKey)
                .isActive(false)
                .totalBalance(0.0)
                .availableBalance(0.0)
                .lockedBalance(0.0)
                .build();

        return accountRepository.save(account);
    }

    public Account updateBalance(Long accountId, Double totalBalance, Double availableBalance, Double lockedBalance) {
        Account account = getAccount(accountId);
        account.setTotalBalance(totalBalance);
        account.setAvailableBalance(availableBalance);
        account.setLockedBalance(lockedBalance);
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

