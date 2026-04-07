package com.example.cryptobot.upbit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpbitOrderChanceDto {

    @JsonProperty("bid_fee")
    private BigDecimal bidFee;

    @JsonProperty("ask_fee")
    private BigDecimal askFee;

    @JsonProperty("maker_bid_fee")
    private BigDecimal makerBidFee;

    @JsonProperty("maker_ask_fee")
    private BigDecimal makerAskFee;

    @JsonProperty("market")
    private MarketInfo market;

    @JsonProperty("bid_account")
    private AccountInfo bidAccount;

    @JsonProperty("ask_account")
    private AccountInfo askAccount;

    @Data
    public static class MarketInfo {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("order_sides")
        private List<String> orderSides;

        @JsonProperty("bid_types")
        private List<String> bidTypes;

        @JsonProperty("ask_types")
        private List<String> askTypes;

        @JsonProperty("bid")
        private CurrencyInfo bid;

        @JsonProperty("ask")
        private CurrencyInfo ask;

        @JsonProperty("max_total")
        private BigDecimal maxTotal;
    }

    @Data
    public static class CurrencyInfo {
        @JsonProperty("currency")
        private String currency;

        @JsonProperty("price_unit")
        private String priceUnit;

        @JsonProperty("min_total")
        private BigDecimal minTotal;
    }

    @Data
    public static class AccountInfo {
        @JsonProperty("currency")
        private String currency;

        @JsonProperty("balance")
        private BigDecimal balance;

        @JsonProperty("locked")
        private BigDecimal locked;

        @JsonProperty("avg_buy_price")
        private BigDecimal avgBuyPrice;

        @JsonProperty("avg_buy_price_modified")
        private Boolean avgBuyPriceModified;

        @JsonProperty("unit_currency")
        private String unitCurrency;
    }
}