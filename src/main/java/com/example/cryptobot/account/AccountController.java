package com.example.cryptobot.account;

import com.example.cryptobot.account.dto.AccountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/me")
    public ResponseEntity<AccountResponse> getMyAccount() {
        return ResponseEntity.ok(AccountResponse.from(accountService.getPrimaryAccount()));
    }

    @PostMapping("/sync")
    public ResponseEntity<AccountResponse> syncAccount() {
        return ResponseEntity.ok(AccountResponse.from(accountService.syncBalanceFromUpbit()));
    }

    @PutMapping("/activate")
    public ResponseEntity<Void> activateAccount() {
        accountService.activateAccount();
        return ResponseEntity.ok().build();
    }

    @PutMapping("/deactivate")
    public ResponseEntity<Void> deactivateAccount() {
        accountService.deactivateAccount();
        return ResponseEntity.ok().build();
    }
}