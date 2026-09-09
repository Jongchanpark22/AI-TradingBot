package com.example.cryptobot.toss;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 토스증권 Open API 설정.
 * toss.securities.* 프로퍼티를 바인딩합니다.
 */
@Component
@ConfigurationProperties(prefix = "toss.securities")
@Getter
@Setter
public class TossSecuritiesProperties {

    /** API Base URL */
    private String baseUrl = "https://openapi.tossinvest.com";

    /** OAuth2 Client ID */
    private String clientId;

    /** OAuth2 Client Secret */
    private String clientSecret;
}
