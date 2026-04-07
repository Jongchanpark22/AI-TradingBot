package com.example.cryptobot.upbit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 업비트 시세 정보 응답 DTO
 * 
 * 업비트 API: GET /v1/ticker
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpbitTickerDto {

    @JsonProperty("market")
    private String market;                      // BTC-KRW, ETH-KRW 등

    @JsonProperty("trade_date")
    private String tradeDate;                   // 20240401

    @JsonProperty("trade_time")
    private String tradeTime;                   // 173015

    @JsonProperty("trade_timestamp")
    private Long tradeTimestamp;                // 1712071815000

    @JsonProperty("opening_price")
    private BigDecimal openingPrice;            // 시가

    @JsonProperty("high_price")
    private BigDecimal highPrice;               // 고가

    @JsonProperty("low_price")
    private BigDecimal lowPrice;                // 저가

    @JsonProperty("trade_price")
    private BigDecimal tradePrice;              // 현재가 (체결가)

    @JsonProperty("prev_closing_price")
    private BigDecimal prevClosingPrice;        // 전일 종가

    @JsonProperty("change")
    private String change;                      // RISE, FALL, EVEN

    @JsonProperty("change_price")
    private BigDecimal changePrice;             // 변동가

    @JsonProperty("change_rate")
    private BigDecimal changeRate;              // 변동률

    @JsonProperty("signed_change_price")
    private BigDecimal signedChangePrice;       // 부호있는 변동가

    @JsonProperty("signed_change_rate")
    private BigDecimal signedChangeRate;        // 부호있는 변동률

    @JsonProperty("trade_volume")
    private BigDecimal tradeVolume;             // 거래량

    @JsonProperty("acc_trade_volume")
    private BigDecimal accTradeVolume;          // 누적 거래량

    @JsonProperty("acc_trade_volume_24h")
    private BigDecimal accTradeVolume24h;       // 24시간 누적 거래량

    @JsonProperty("acc_trade_price")
    private BigDecimal accTradePrice;           // 누적 거래대금

    @JsonProperty("acc_trade_price_24h")
    private BigDecimal accTradePrice24h;        // 24시간 누적 거래대금

    @JsonProperty("highest_52_week_price")
    private BigDecimal highest52WeekPrice;      // 52주 고가

    @JsonProperty("highest_52_week_date")
    private String highest52WeekDate;           // 52주 고가 날짜

    @JsonProperty("lowest_52_week_price")
    private BigDecimal lowest52WeekPrice;       // 52주 저가

    @JsonProperty("lowest_52_week_date")
    private String lowest52WeekDate;            // 52주 저가 날짜

    @JsonProperty("market_state")
    private String marketState;                 // ACTIVE, INACTIVE

    @JsonProperty("is_trading_suspended")
    private Boolean isTradingSuspended;         // 거래 중단 여부

    @JsonProperty("delisting_date")
    private String delistingDate;               // 상장폐지일

    @JsonProperty("market_warning")
    private String marketWarning;               // NONE, CAUTION

    @JsonProperty("timestamp")
    private Long timestamp;                     // 타임스탬프

    @JsonProperty("ab_ratio")
    private BigDecimal abRatio;                 // 매수/매도 비율
}

