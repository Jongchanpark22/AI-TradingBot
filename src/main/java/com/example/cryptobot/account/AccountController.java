package com.example.cryptobot.account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{accountId}")
    public ResponseEntity<Account> getAccount(@PathVariable Long accountId) {
        Account account = accountService.getAccount(accountId);
        return ResponseEntity.ok(account);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Account>> getAccountsByUserId(@PathVariable Long userId) {
        List<Account> accounts = accountService.getAccountsByUserId(userId);
        return ResponseEntity.ok(accounts);
    }

    @PostMapping
    public ResponseEntity<Account> createAccount(@RequestBody CreateAccountRequest request) {
        Account account = accountService.createAccount(
                request.userId,
                request.exchangeType,
                request.apiKey,
                request.secretKey
        );
        return ResponseEntity.ok(account);
    }

    @PutMapping("/{accountId}/activate")
    public ResponseEntity<Void> activateAccount(@PathVariable Long accountId) {
        accountService.activateAccount(accountId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{accountId}/deactivate")
    public ResponseEntity<Void> deactivateAccount(@PathVariable Long accountId) {
        accountService.deactivateAccount(accountId);
        return ResponseEntity.ok().build();
    }

    public static class CreateAccountRequest {
        public Long userId;
        public Account.ExchangeType exchangeType;
        public String apiKey;
        public String secretKey;
    }

}

