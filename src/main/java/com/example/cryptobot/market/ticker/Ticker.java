package com.example.cryptobot.market.ticker;

import com.example.cryptobot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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
    private BigDecimal currentPrice;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private BigDecimal highPrice24h;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private BigDecimal lowPrice24h;

    @Column(columnDefinition = "DECIMAL(38,8)")
    private BigDecimal volume24h;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private BigDecimal changePercent24h;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private BigDecimal bid;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private BigDecimal ask;

    @Column(columnDefinition = "DECIMAL(19,8)")
    private BigDecimal bidVolume;

    @Column(columnDefinition = "DECIMAL(19,8)")
    private BigDecimal askVolume;

    @Column(columnDefinition = "DECIMAL(19,2)")
    private BigDecimal marketCap;
}

