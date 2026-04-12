package com.example.cryptobot.exchange.upbit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpbitMarketDto {

    @JsonProperty("market")
    private String market;

    @JsonProperty("korean_name")
    private String koreanName;

    @JsonProperty("english_name")
    private String englishName;

    @JsonProperty("market_event")
    private MarketEvent marketEvent;

    @Data
    @NoArgsConstructor
    public static class MarketEvent {
        @JsonProperty("caution")
        private Caution caution;

        @Data
        @NoArgsConstructor
        public static class Caution {
            @JsonProperty("PRICE_FLUCTUATIONS")
            private boolean priceFluctuations;
            @JsonProperty("TRADING_VOLUME_SOARING")
            private boolean tradingVolumeSoaring;
        }
    }
}
