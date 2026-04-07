package com.example.cryptobot.strategy.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "bot.trading")
public class TradingProperties {

    /**
     * TEST / LIVE
     */
    private String mode = "TEST";

    /**
     * 상위 몇 개 코인을 볼지
     */
    private int topCoinCount = 5;

    /**
     * 1회 매수 금액
     */
    private BigDecimal orderAmount = new BigDecimal("100000");

    /**
     * 목표 수익률 (%)
     */
    private BigDecimal takeProfitRate = new BigDecimal("5.0");

    /**
     * 손절 수익률 (%)
     */
    private BigDecimal stopLossRate = new BigDecimal("-2.0");

    /**
     * 재진입 쿨다운 (분)
     */
    private long reentryCooldownMinutes = 10;

    /**
     * AI 판별 사용 여부
     */
    private boolean aiEnabled = false;
}