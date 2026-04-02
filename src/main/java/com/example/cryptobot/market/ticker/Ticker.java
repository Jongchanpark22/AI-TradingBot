package com.example.cryptobot.market.ticker;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tickers", indexes = {
    @Index(name = "idx_symbol", columnList = "symbol", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticker extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String symbol;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double currentPrice;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double highPrice24h;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double lowPrice24h;

    @Column(columnDefinition = "DECIMAL(19,8)")
    private Double volume24h;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double changePercent24h;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double bid;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double ask;

    @Column(columnDefinition = "DECIMAL(19,8)")
    private Double bidVolume;

    @Column(columnDefinition = "DECIMAL(19,8)")
    private Double askVolume;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private Double marketCap;

}

