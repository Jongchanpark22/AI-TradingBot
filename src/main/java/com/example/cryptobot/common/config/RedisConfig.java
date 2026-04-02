package com.example.cryptobot.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 캐싱 설정
 * 
 * 용도:
 * 1. 시세 정보 (Ticker) 캐싱 - 5분 TTL
 * 2. 캔들 데이터 캐싱 - 1시간 TTL
 * 3. 거래 신호 이력 - 24시간 TTL
 * 4. 거래 로그 - 7일 TTL
 */
@Configuration
@EnableCaching
public class RedisConfig {
    // Spring Boot 자동 Redis 캐싱 설정 사용
    // application.yml에서 TTL 및 직렬화 설정 관리
}

