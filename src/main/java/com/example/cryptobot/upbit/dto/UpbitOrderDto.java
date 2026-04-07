package com.example.cryptobot.upbit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 업비트 주문 응답 DTO
 * 
 * 업비트 API: POST /v1/orders
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpbitOrderDto {

    @JsonProperty("uuid")
    private String uuid;                        // 주문 UUID

    @JsonProperty("side")
    private String side;                        // bid (매수), ask (매도)

    @JsonProperty("ord_type")
    private String ordType;                     // limit (지정가), market (시장가)

    @JsonProperty("price")
    private BigDecimal price;                   // 주문 가격

    @JsonProperty("state")
    private String state;                       // wait, watch, done, cancel

    @JsonProperty("market")
    private String market;                      // BTC-KRW, ETH-KRW 등

    @JsonProperty("created_at")
    private String createdAt;                   // 주문 생성 시간

    @JsonProperty("volume")
    private BigDecimal volume;                  // 주문 수량

    @JsonProperty("remaining_volume")
    private BigDecimal remainingVolume;         // 남은 수량

    @JsonProperty("reserved_fee")
    private BigDecimal reservedFee;             // 예약된 수수료

    @JsonProperty("remaining_fee")
    private BigDecimal remainingFee;            // 남은 수수료

    @JsonProperty("paid_fee")
    private BigDecimal paidFee;                 // 지불한 수수료

    @JsonProperty("locked")
    private BigDecimal locked;                  // 잠금 금액

    @JsonProperty("executed_volume")
    private BigDecimal executedVolume;          // 체결된 수량

    @JsonProperty("trades_count")
    private Integer tradesCount;                // 체결 횟수

    @JsonProperty("trades")
    private Object trades;                      // 체결 정보 상세
}

