package com.example.cryptobot.upbit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 업비트 캔들 정보 응답 DTO
 * 
 * 업비트 API: GET /v1/candles/minutes/:unit
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpbitCandleDto {

    @JsonProperty("market")
    private String market;                      // BTC-KRW, ETH-KRW 등

    @JsonProperty("candle_date_time_kst")
    private String candleDateTimeKst;           // 2024-04-01T17:30:00+09:00

    @JsonProperty("candle_date_time_utc")
    private String candleDateTimeUtc;           // 2024-04-01T08:30:00Z

    @JsonProperty("opening_price")
    private BigDecimal openingPrice;            // 시가

    @JsonProperty("high_price")
    private BigDecimal highPrice;               // 고가

    @JsonProperty("low_price")
    private BigDecimal lowPrice;                // 저가

    @JsonProperty("trade_price")
    private BigDecimal tradePrice;              // 종가

    @JsonProperty("timestamp")
    private Long timestamp;                     // 타임스탬프 (ms)

    @JsonProperty("candle_acc_trade_price")
    private BigDecimal candleAccTradePrice;     // 누적 거래대금

    @JsonProperty("candle_acc_trade_volume")
    private BigDecimal candleAccTradeVolume;    // 누적 거래량

    @JsonProperty("unit")
    private Integer unit;                       // 1, 5, 15, 30, 60, 240 (분)
}

