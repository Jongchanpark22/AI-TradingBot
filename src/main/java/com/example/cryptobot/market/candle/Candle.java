package com.example.cryptobot.market.candle;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "candles", indexes = {
    @Index(name = "idx_symbol_period", columnList = "symbol,period"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CandlePeriod period;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double openPrice;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double highPrice;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double lowPrice;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double closePrice;

    @Column(columnDefinition = "DECIMAL(19,8)")
    private Double volume;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double quoteAssetVolume;

    public enum CandlePeriod {
        ONE_MIN, FIVE_MIN, FIFTEEN_MIN, THIRTY_MIN, ONE_HOUR, FOUR_HOUR, ONE_DAY, ONE_WEEK
    }

}

