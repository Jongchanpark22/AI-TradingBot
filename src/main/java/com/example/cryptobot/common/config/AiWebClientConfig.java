package com.example.cryptobot.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Python ML 서버용 WebClient 빈 설정.
 *
 * <p>AiSignalClient에서 @Qualifier("aiWebClient")로 주입해 사용한다.
 * base-url은 application.yml의 trading.ai.base-url로 설정한다.
 */
@Configuration
public class AiWebClientConfig {

    @Bean("aiWebClient")
    public WebClient aiWebClient(
            WebClient.Builder builder,
            @Value("${trading.ai.base-url:http://localhost:8000}") String baseUrl) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
