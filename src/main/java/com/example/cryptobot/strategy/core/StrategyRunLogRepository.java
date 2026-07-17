package com.example.cryptobot.strategy.core;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface StrategyRunLogRepository extends JpaRepository<StrategyRunLog, Long> {

    /** 재실행 시 중복 방지 — 특정 source의 로그 전체 삭제 */
    @Modifying
    @Transactional
    int deleteBySource(String source);
}