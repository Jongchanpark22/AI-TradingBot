package com.example.cryptobot.exchange.upbit.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class UpbitClientConfig {

    @Bean
    @org.springframework.context.annotation.Primary
    public RestTemplate upbitRestTemplate(UpbitApiProperties properties) {
        return new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
    }

    /**
     * Gemini 전용 RestTemplate.
     * Gemini thinking 모델은 응답에 수십 초가 소요될 수 있어 read timeout을 60s로 설정합니다.
     */
    @Bean
    public RestTemplate geminiRestTemplate() {
        return new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }
}