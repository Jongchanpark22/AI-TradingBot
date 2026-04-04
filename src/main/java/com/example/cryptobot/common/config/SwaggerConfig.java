package com.example.cryptobot.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI 설정
 * 
 * Swagger UI 접근: http://localhost:8080/api/swagger-ui.html
 * OpenAPI JSON: http://localhost:8080/api/v3/api-docs
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .addServersItem(localServer());
    }

    /**
     * API 정보
     */
    private Info apiInfo() {
        return new Info()
                .title("AI Trading Bot API")
                .description("암호화폐 자동 거래 봇 - 업비트 API 통합")
                .version("1.0.0")
                .contact(contactInfo())
                .license(licenseInfo());
    }

    /**
     * 연락처 정보
     */
    private Contact contactInfo() {
        return new Contact()
                .name("AI Trading Bot Team")
                .url("https://github.com/Jongchanpark22/AI-TradingBot")
                .email("support@example.com");
    }

    /**
     * 라이선스 정보
     */
    private License licenseInfo() {
        return new License()
                .name("MIT License")
                .url("https://opensource.org/licenses/MIT");
    }

    /**
     * 로컬 서버 정보
     */
    private Server localServer() {
        return new Server()
                .url("http://localhost:8080/api")
                .description("Local Development Server");
    }
}

