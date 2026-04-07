package com.example.cryptobot.market.candle;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "candles",
        indexes = {
                @Index(name = "idx_symbol_period", columnList = "symbol,period"),
                @Index(name = "idx_timestamp", columnList = "timestamp")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_candle_symbol_period_timestamp",
                        columnNames = {"symbol", "period", "timestamp"}
                )
        }
)
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

    @Column(precision = 19, scale = 2)
    private BigDecimal openPrice;

    @Column(precision = 19, scale = 2)
    private BigDecimal highPrice;

    @Column(precision = 19, scale = 2)
    private BigDecimal lowPrice;

    @Column(precision = 19, scale = 2)
    private BigDecimal closePrice;

    @Column(precision = 19, scale = 8)
    private BigDecimal volume;

    @Column(precision = 19, scale = 2)
    private BigDecimal quoteAssetVolume;

    public enum CandlePeriod {
        ONE_MIN, FIVE_MIN, FIFTEEN_MIN, THIRTY_MIN, ONE_HOUR, FOUR_HOUR, ONE_DAY, ONE_WEEK
    }
}