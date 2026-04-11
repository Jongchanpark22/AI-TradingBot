package com.example.cryptobot.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Redis 캐시를 이용한 거래 로그 서비스
 * 
 * 용도:
 * - 실시간 거래 로그 저장 (7일)
 * - 거래 이력 빠른 조회
 * - 성과 분석 데이터
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeLogCacheService {

    /**
     * 거래 로그 조회 (캐시됨)
     * Key: "account:date" (예: "1:2024-04-01")
     * TTL: 7일
     */
    @Cacheable(value = "tradelog", key = "#accountId + ':' + #date", unless = "#result == null")
    public String getTradeLogFromCache(Long accountId, String date) {
        log.debug("TradeLog 캐시 미스: {}:{}", accountId, date);
        return null;
    }

    /**
     * 거래 로그 저장 (캐시 갱신)
     */
    @CachePut(value = "tradelog", key = "#accountId + ':' + #date")
    public String updateTradeLogCache(Long accountId, String date, String logData) {
        log.debug("TradeLog 캐시 저장: {}:{}", accountId, date);
        return logData;
    }

    /**
     * 특정 거래 로그 캐시 삭제
     */
    @CacheEvict(value = "tradelog", key = "#accountId + ':' + #date")
    public void evictTradeLogCache(Long accountId, String date) {
        log.debug("TradeLog 캐시 삭제: {}:{}", accountId, date);
    }

    /**
     * 모든 거래 로그 캐시 삭제
     */
    @CacheEvict(value = "tradelog", allEntries = true)
    public void evictAllTradeLogCache() {
        log.info("모든 TradeLog 캐시 삭제");
    }
}

