package com.example.cryptobot.account.dto;

import com.example.cryptobot.account.Account;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {

    private Long id;
    private Boolean isActive;
    private BigDecimal totalBalance;
    private BigDecimal availableBalance;
    private BigDecimal lockedBalance;

    public static AccountResponse from(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .isActive(account.getIsActive())
                .totalBalance(account.getTotalBalance())
                .availableBalance(account.getAvailableBalance())
                .lockedBalance(account.getLockedBalance())
                .build();
    }
}