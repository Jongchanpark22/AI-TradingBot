package com.example.cryptobot.account;

import com.example.cryptobot.account.dto.AccountResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
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
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long accountId) {
        Account account = accountService.getAccount(accountId);
        return ResponseEntity.ok(AccountResponse.from(account));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AccountResponse>> getAccountsByUserId(@PathVariable Long userId) {
        List<AccountResponse> accounts = accountService.getAccountsByUserId(userId)
                .stream()
                .map(AccountResponse::from)
                .toList();

        return ResponseEntity.ok(accounts);
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@RequestBody CreateAccountRequest request) {
        Account account = accountService.createAccount(
                request.getUserId(),
                request.getApiKey(),
                request.getSecretKey()
        );
        return ResponseEntity.ok(AccountResponse.from(account));
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

    @Getter
    @Setter
    public static class CreateAccountRequest {
        private Long userId;
        private String apiKey;
        private String secretKey;
    }
}