package com.example.cryptobot.chart;

import com.example.cryptobot.market.candle.Candle;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * React Native 차트용 캔들 응답 DTO.
 * TradingView Lightweight Charts 형식에 맞춰 time, open, high, low, close 기본 제공.
 */
@Getter
@Builder
public class CandleDto {

    private LocalDateTime time;
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Double volume;

    /** 이동평균 오버레이 (EMA12) */
    private Double ema12;

    /** 이동평균 오버레이 (EMA26) */
    private Double ema26;

    /** 볼린저밴드 (최신 캔들만 채움, 나머지 null) */
    private Double bbUpper;
    private Double bbLower;
    private Double bbMiddle;

    public static CandleDto from(Candle c,
                                 Double ema12, Double ema26,
                                 Double bbUpper, Double bbLower, Double bbMiddle) {
        return CandleDto.builder()
                .time(c.getTimestamp())
                .open(c.getOpenPrice() != null ? c.getOpenPrice().doubleValue() : null)
                .high(c.getHighPrice() != null ? c.getHighPrice().doubleValue() : null)
                .low(c.getLowPrice() != null ? c.getLowPrice().doubleValue() : null)
                .close(c.getClosePrice() != null ? c.getClosePrice().doubleValue() : null)
                .volume(c.getVolume() != null ? c.getVolume().doubleValue() : null)
                .ema12(ema12)
                .ema26(ema26)
                .bbUpper(bbUpper)
                .bbLower(bbLower)
                .bbMiddle(bbMiddle)
                .build();
    }
}
