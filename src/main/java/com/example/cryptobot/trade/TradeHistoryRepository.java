package com.example.cryptobot.trade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradeHistoryRepository extends JpaRepository<TradeHistory, Long> {

    List<TradeHistory> findByAccountIdOrderByExitTimeDesc(Long accountId);

    List<TradeHistory> findByAccountIdAndSymbolOrderByExitTimeDesc(Long accountId, String symbol);

    List<TradeHistory> findByAccountIdAndExitTimeBetweenOrderByExitTimeDesc(
            Long accountId, LocalDateTime from, LocalDateTime to);

    /** 오늘 실현 손익 합계 */
    @Query("""
            SELECT COALESCE(SUM(t.profitAmount), 0)
            FROM TradeHistory t
            WHERE t.account.id = :accountId
              AND t.exitTime >= :startOfDay
            """)
    java.math.BigDecimal sumProfitToday(
            @Param("accountId") Long accountId,
            @Param("startOfDay") LocalDateTime startOfDay);

    /** 누적 통계용 */
    @Query("""
            SELECT COUNT(t), SUM(t.profitAmount), AVG(t.profitRate)
            FROM TradeHistory t
            WHERE t.account.id = :accountId
            """)
    Object[] aggregateStats(@Param("accountId") Long accountId);

    /** 손절 쿨다운 체크: 특정 심볼에서 지정 시간 이후 해당 exitType 거래 이력 존재 여부 */
    boolean existsByAccountIdAndSymbolAndExitTypeAndExitTimeAfter(
            Long accountId, String symbol, TradeHistory.ExitType exitType, LocalDateTime after);

    /** 재실행 시 중복 방지 — 특정 source의 거래 이력 전체 삭제 */
    @Modifying
    @Transactional
    int deleteBySource(String source);

    /**
     * 특정 심볼의 과거 거래 통계 (기능1 과거 통계 전망용).
     * [총 건수, 수익 건수, 최소 손익률, 최대 손익률, 평균 손익률]
     */
    @Query("""
            SELECT COUNT(t),
                   SUM(CASE WHEN t.profitRate > 0 THEN 1 ELSE 0 END),
                   MIN(t.profitRate),
                   MAX(t.profitRate),
                   AVG(t.profitRate)
            FROM TradeHistory t
            WHERE t.symbol = :symbol
            """)
    Object[] symbolStats(@Param("symbol") String symbol);
}
