package com.example.cryptobot.holding;

import com.example.cryptobot.holding.dto.HoldingRequest;
import com.example.cryptobot.holding.dto.HoldingResponse;
import com.example.cryptobot.holding.dto.HoldingStatsResponse;
import com.example.cryptobot.holding.dto.WatchlistRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 내 종목 관리 API.
 *
 * <p>⚠️ 면책: 제공되는 통계·지표는 과거 데이터 기반 참고 수치이며 투자 권유가 아닙니다.</p>
 */
@RestController
@RequiredArgsConstructor
public class HoldingController {

    private final HoldingService holdingService;

    // ─── 보유 종목 ────────────────────────────────────────────────────────────

    /**
     * 보유 종목 전체 조회 (현재가 + 미실현 손익 포함).
     */
    @GetMapping("/holdings")
    public List<HoldingResponse> getAllHoldings() {
        return holdingService.findAllHoldings();
    }

    /**
     * 보유 종목 단건 조회.
     */
    @GetMapping("/holdings/{id}")
    public HoldingResponse getHolding(@PathVariable Long id) {
        return holdingService.findHoldingById(id);
    }

    /**
     * 보유 종목 추가.
     */
    @PostMapping("/holdings")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldingResponse createHolding(@RequestBody HoldingRequest request) {
        return holdingService.createHolding(request);
    }

    /**
     * 보유 종목 수정 (평균매수가·수량·메모).
     */
    @PutMapping("/holdings/{id}")
    public HoldingResponse updateHolding(@PathVariable Long id, @RequestBody HoldingRequest request) {
        return holdingService.updateHolding(id, request);
    }

    /**
     * 보유 종목 삭제.
     */
    @DeleteMapping("/holdings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHolding(@PathVariable Long id) {
        holdingService.deleteHolding(id);
    }

    /**
     * 보유 종목 과거 통계.
     * ⚠️ 참고용 수치, 투자 권유 아님, 표본이 적으면 신뢰도 낮음.
     */
    @GetMapping("/holdings/{id}/stats")
    public HoldingStatsResponse getStats(@PathVariable Long id) {
        return holdingService.getStats(id);
    }

    // ─── 관심 종목 ────────────────────────────────────────────────────────────

    /**
     * 관심 종목 전체 조회.
     */
    @GetMapping("/watchlist")
    public List<Watchlist> getWatchlist() {
        return holdingService.findAllWatchlist();
    }

    /**
     * 관심 종목 추가.
     */
    @PostMapping("/watchlist")
    @ResponseStatus(HttpStatus.CREATED)
    public Watchlist addWatchlist(@RequestBody WatchlistRequest request) {
        return holdingService.addWatchlist(request);
    }

    /**
     * 관심 종목 삭제.
     */
    @DeleteMapping("/watchlist/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWatchlist(@PathVariable Long id) {
        holdingService.deleteWatchlist(id);
    }
}
