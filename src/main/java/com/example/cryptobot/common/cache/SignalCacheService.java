package com.example.cryptobot.common.cache;

import com.example.cryptobot.strategy.hybrid.HybridSignalAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Redis 캐시를 이용한 거래 신호 서비스
 * 
 * 용도:
 * - 최근 거래 신호 이력 저장 (24시간)
 * - 신호 재확인 시 빠른 조회
 * - 거래 분석 데이터
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignalCacheService {

    /**
     * 거래 신호 조회 (캐시됨)
     * Key: "symbol:timeframe" (예: "BTC:1H")
     * TTL: 24시간
     */
    @Cacheable(value = "signal", key = "#symbol + ':' + #timeframe", unless = "#result == null")
    public HybridSignalAnalyzer.TradeSignal getSignalFromCache(String symbol, String timeframe) {
        log.debug("Signal 캐시 미스: {}:{}", symbol, timeframe);
        return null;
    }

    /**
     * 거래 신호 저장 (캐시 갱신)
     */
    @CachePut(value = "signal", key = "#symbol + ':' + #timeframe")
    public HybridSignalAnalyzer.TradeSignal updateSignalCache(
            String symbol, String timeframe, HybridSignalAnalyzer.TradeSignal signal) {
        log.info("Signal 캐시 저장: {}:{} = {}", symbol, timeframe, signal.getSignal());
        return signal;
    }

    /**
     * 특정 신호 캐시 삭제
     */
    @CacheEvict(value = "signal", key = "#symbol + ':' + #timeframe")
    public void evictSignalCache(String symbol, String timeframe) {
        log.debug("Signal 캐시 삭제: {}:{}", symbol, timeframe);
    }

    /**
     * 모든 신호 캐시 삭제
     */
    @CacheEvict(value = "signal", allEntries = true)
    public void evictAllSignalCache() {
        log.info("모든 Signal 캐시 삭제");
    }
}

