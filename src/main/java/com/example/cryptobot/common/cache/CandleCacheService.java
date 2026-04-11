package com.example.cryptobot.common.cache;

import com.example.cryptobot.market.candle.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Redis 캐시를 이용한 캔들 데이터 서비스
 * 
 * 용도:
 * - 최근 캔들 데이터 빠르게 조회 (1시간 캐시)
 * - 기술적 지표 계산 시 캔들 데이터 조회
 * - 데이터베이스 부하 감소
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleCacheService {

    /**
     * 캔들 데이터 조회 (캐시됨)
     * Key: "symbol:period" (예: "BTC:ONE_HOUR")
     * TTL: 1시간
     */
    @Cacheable(value = "candle", key = "#symbol + ':' + #period.name()", unless = "#result == null")
    public List<Candle> getCandlesFromCache(String symbol, Candle.CandlePeriod period) {
        log.debug("Candle 캐시 미스: {}:{}", symbol, period);
        return null;
    }

    /**
     * 캔들 데이터 업데이트 (캐시 갱신)
     */
    @CachePut(value = "candle", key = "#symbol + ':' + #period.name()")
    public List<Candle> updateCandleCache(String symbol, Candle.CandlePeriod period, List<Candle> candles) {
        log.debug("Candle 캐시 업데이트: {}:{} ({}개)", symbol, period, candles.size());
        return candles;
    }

    /**
     * 특정 캔들 캐시 삭제
     */
    @CacheEvict(value = "candle", key = "#symbol + ':' + #period.name()")
    public void evictCandleCache(String symbol, Candle.CandlePeriod period) {
        log.debug("Candle 캐시 삭제: {}:{}", symbol, period);
    }

    /**
     * 특정 심볼의 모든 캔들 캐시 삭제
     */
    @CacheEvict(value = "candle", allEntries = true)
    public void evictAllCandleCache() {
        log.info("모든 Candle 캐시 삭제");
    }
}

