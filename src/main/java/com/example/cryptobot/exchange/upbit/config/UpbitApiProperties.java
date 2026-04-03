package com.example.cryptobot.exchange.upbit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "upbit.api")
@Data
public class UpbitApiProperties {
    private String baseUrl;
    private long timeoutMs = 10000;
    private String accessKey;
    private String secretKey;
}