package com.example.cryptobot.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "trading.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class SchedulerConfig {

    @PostConstruct
    public void init() {
        log.info("[Scheduler] 전략 스케줄러 활성화 — 1시간(매 정시 10초) / 4시간(0,4,8,12,16,20시)");
    }
}
