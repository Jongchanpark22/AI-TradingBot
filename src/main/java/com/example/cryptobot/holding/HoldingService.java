package com.example.cryptobot.holding;

import com.example.cryptobot.holding.dto.HoldingRequest;
import com.example.cryptobot.holding.dto.HoldingResponse;
import com.example.cryptobot.holding.dto.HoldingStatsResponse;
import com.example.cryptobot.holding.dto.WatchlistRequest;
import com.example.cryptobot.market.ticker.Ticker;
import com.example.cryptobot.market.ticker.TickerRepository;
import com.example.cryptobot.trade.TradeHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 보유 종목·관심 종목 관리 서비스.
 * 현재가는 Ticker 캐시에서 조회하며, 과거 통계는 trade_history 재활용합니다.
 * 모든 조회·수정·삭제는 userId 소유권을 검증합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingService {

    private final UserHoldingRepository holdingRepository;
    private final WatchlistRepository watchlistRepository;
    private final TickerRepository tickerRepository;
    private final TradeHistoryRepository tradeHistoryRepository;

    // ─── 보유 종목 CRUD ─────────────────────────────────────────────────────────

    /**
     * 내 보유 종목 전체 조회 (현재가 + 미실현 손익 포함).
     *
     * @param userId 요청 회원 ID
     */
    public List<HoldingResponse> findAllHoldings(Long userId) {
        return holdingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(h -> {
                    BigDecimal price = fetchCurrentPrice(h.getSymbol());
                    return HoldingResponse.from(h, price);
                })
                .toList();
    }

    /**
     * 내 보유 종목 단건 조회.
     *
     * @param userId 요청 회원 ID
     * @param id     보유 종목 ID
     */
    public HoldingResponse findHoldingById(Long userId, Long id) {
        UserHolding holding = holdingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("보유 종목을 찾을 수 없습니다: " + id));
        return HoldingResponse.from(holding, fetchCurrentPrice(holding.getSymbol()));
    }

    /**
     * 보유 종목 추가.
     *
     * @param userId  소유자 ID
     * @param request 종목 정보
     */
    @Transactional
    public HoldingResponse createHolding(Long userId, HoldingRequest request) {
        UserHolding holding = UserHolding.builder()
                .userId(userId)
                .symbol(request.getSymbol())
                .avgBuyPrice(request.getAvgBuyPrice())
                .quantity(request.getQuantity())
                .memo(request.getMemo())
                .build();
        UserHolding saved = holdingRepository.save(holding);
        return HoldingResponse.from(saved, fetchCurrentPrice(saved.getSymbol()));
    }

    /**
     * 보유 종목 수정 (평균 매수가, 수량, 메모).
     *
     * @param userId  요청 회원 ID (소유권 검증)
     * @param id      보유 종목 ID
     * @param request 수정할 필드
     */
    @Transactional
    public HoldingResponse updateHolding(Long userId, Long id, HoldingRequest request) {
        UserHolding holding = holdingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("보유 종목을 찾을 수 없습니다: " + id));

        if (request.getAvgBuyPrice() != null) holding.setAvgBuyPrice(request.getAvgBuyPrice());
        if (request.getQuantity() != null) holding.setQuantity(request.getQuantity());
        if (request.getMemo() != null) holding.setMemo(request.getMemo());

        UserHolding saved = holdingRepository.save(holding);
        return HoldingResponse.from(saved, fetchCurrentPrice(saved.getSymbol()));
    }

    /**
     * 보유 종목 삭제.
     *
     * @param userId 요청 회원 ID (소유권 검증)
     * @param id     보유 종목 ID
     */
    @Transactional
    public void deleteHolding(Long userId, Long id) {
        UserHolding holding = holdingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("보유 종목을 찾을 수 없습니다: " + id));
        holdingRepository.delete(holding);
    }

    // ─── 과거 통계 ────────────────────────────────────────────────────────────

    /**
     * 해당 심볼의 과거 거래 통계 반환.
     * trade_history 재활용 — 결과는 참고용이며 투자 권유가 아닙니다.
     *
     * @param userId 요청 회원 ID (소유권 검증)
     * @param id     보유 종목 ID
     * @return 과거 통계 (표본 수, 수익 건수, 손익률 분포)
     */
    public HoldingStatsResponse getStats(Long userId, Long id) {
        UserHolding holding = holdingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("보유 종목을 찾을 수 없습니다: " + id));

        Object[] row = tradeHistoryRepository.symbolStats(holding.getSymbol());

        long total = 0L;
        long profitable = 0L;
        Double minRate = null, maxRate = null, avgRate = null;

        if (row != null && row.length >= 5 && row[0] != null) {
            total = ((Number) row[0]).longValue();
            profitable = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            minRate = row[2] != null ? ((Number) row[2]).doubleValue() : null;
            maxRate = row[3] != null ? ((Number) row[3]).doubleValue() : null;
            avgRate = row[4] != null ? ((Number) row[4]).doubleValue() : null;
        }

        return HoldingStatsResponse.builder()
                .symbol(holding.getSymbol())
                .totalTrades(total)
                .profitableTrades(profitable)
                .lossTrades(total - profitable)
                .minProfitRate(minRate)
                .maxProfitRate(maxRate)
                .avgProfitRate(avgRate)
                .build();
    }

    // ─── 관심 종목 CRUD ──────────────────────────────────────────────────────────

    /**
     * 내 관심 종목 전체 조회.
     *
     * @param userId 요청 회원 ID
     */
    public List<Watchlist> findAllWatchlist(Long userId) {
        return watchlistRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 관심 종목 추가.
     *
     * @param userId  소유자 ID
     * @param request 종목 정보
     */
    @Transactional
    public Watchlist addWatchlist(Long userId, WatchlistRequest request) {
        Watchlist item = Watchlist.builder()
                .userId(userId)
                .symbol(request.getSymbol())
                .memo(request.getMemo())
                .build();
        return watchlistRepository.save(item);
    }

    /**
     * 관심 종목 삭제.
     *
     * @param userId 요청 회원 ID (소유권 검증)
     * @param id     관심 종목 ID
     */
    @Transactional
    public void deleteWatchlist(Long userId, Long id) {
        Watchlist item = watchlistRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("관심 종목을 찾을 수 없습니다: " + id));
        watchlistRepository.delete(item);
    }

    // ─── 내부 유틸 ────────────────────────────────────────────────────────────

    /**
     * TickerRepository에서 최신 시세 조회.
     * 캐시 미스 시 null 반환 (시세 없음으로 표시).
     */
    private BigDecimal fetchCurrentPrice(String symbol) {
        try {
            return tickerRepository.findBySymbol(symbol)
                    .map(Ticker::getCurrentPrice)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("현재가 조회 실패: {}", symbol, e);
            return null;
        }
    }
}
