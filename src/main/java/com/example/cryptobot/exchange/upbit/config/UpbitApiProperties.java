package com.example.cryptobot.exchange.upbit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 업비트 API 설정 프로퍼티
 */
@Component
@ConfigurationProperties(prefix = "upbit.api")
@Data
public class UpbitApiProperties {
    private String baseUrl;           // https://api.upbit.com
    private long timeoutMs;           // 10000ms
    private String accessKey;         // UPBIT_ACCESS_KEY 환경변수
    private String secretKey;         // UPBIT_SECRET_KEY 환경변수
}

