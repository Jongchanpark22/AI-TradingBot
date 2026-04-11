package com.example.cryptobot.exchange.upbit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 업비트 계정 정보 응답 DTO
 * 
 * 업비트 API: GET /v1/accounts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpbitAccountDto {

    @JsonProperty("currency")
    private String currency;                    // KRW, BTC, ETH 등

    @JsonProperty("balance")
    private BigDecimal balance;                 // 잔액

    @JsonProperty("locked")
    private BigDecimal locked;                  // 잠금 금액

    @JsonProperty("avg_buy_price")
    private BigDecimal avgBuyPrice;             // 평균 매수가

    @JsonProperty("avg_buy_price_modified")
    private Boolean avgBuyPriceModified;        // 평균 매수가 수정 여부

    @JsonProperty("unit_currency")
    private String unitCurrency;                // KRW
}

