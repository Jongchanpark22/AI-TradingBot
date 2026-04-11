package com.example.cryptobot.common.cache;

import com.example.cryptobot.market.ticker.Ticker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Redis 캐시를 이용한 시세 정보 서비스
 * 
 * 용도:
 * - 최신 시세 정보 빠르게 조회 (5분 캐시)
 * - 거래 신호 생성 시 현재가 조회
 * - 전체 시장 현황 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TickerCacheService {

    /**
     * 시세 정보 조회 (캐시됨)
     * TTL: 5분
     */
    @Cacheable(value = "ticker", key = "#symbol", unless = "#result == null")
    public Ticker getTickerFromCache(String symbol) {
        log.debug("Ticker 캐시 미스: {}", symbol);
        return null;
    }

    /**
     * 시세 정보 업데이트 (캐시 갱신)
     * 거래소 API에서 조회한 데이터를 캐시에 저장
     */
    @CachePut(value = "ticker", key = "#ticker.symbol")
    public Ticker updateTickerCache(Ticker ticker) {
        log.debug("Ticker 캐시 업데이트: {}", ticker.getSymbol());
        return ticker;
    }

    /**
     * 특정 시세 캐시 삭제
     */
    @CacheEvict(value = "ticker", key = "#symbol")
    public void evictTickerCache(String symbol) {
        log.debug("Ticker 캐시 삭제: {}", symbol);
    }

    /**
     * 모든 시세 캐시 삭제
     */
    @CacheEvict(value = "ticker", allEntries = true)
    public void evictAllTickerCache() {
        log.info("모든 Ticker 캐시 삭제");
    }
}

