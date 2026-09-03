package com.example.cryptobot.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 작업 설정.
 * 리포트 생성 등 장시간 작업을 전용 스레드풀에서 실행합니다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 리포트 생성 전용 스레드풀.
     * core 2, max 4, 대기열 20 — 동시 생성 수를 제한하여 Gemini API 과부하를 방지합니다.
     */
    @Bean("reportExecutor")
    public org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor reportExecutor() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor exec =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(20);
        exec.setThreadNamePrefix("report-async-");
        // 큐 초과 시 호출 스레드에서 직접 실행 (요청 손실 방지)
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }
}
