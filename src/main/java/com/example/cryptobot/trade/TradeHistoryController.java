package com.example.cryptobot.trade;

import com.example.cryptobot.account.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/trade-history")
@RequiredArgsConstructor
public class TradeHistoryController {

    private final TradeHistoryService tradeHistoryService;
    private final AccountService accountService;

    /** 전체 거래 내역 (최신순) */
    @GetMapping
    public ResponseEntity<List<TradeHistory>> getAll() {
        Long accountId = accountService.getPrimaryAccount().getId();
        return ResponseEntity.ok(tradeHistoryService.getAll(accountId));
    }

    /** 특정 코인 거래 내역 */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<List<TradeHistory>> getBySymbol(@PathVariable String symbol) {
        Long accountId = accountService.getPrimaryAccount().getId();
        return ResponseEntity.ok(tradeHistoryService.getBySymbol(accountId, symbol));
    }

    /**
     * 날짜 범위 조회
     * 예) /trade-history/range?from=2024-04-01&to=2024-04-18
     */
    @GetMapping("/range")
    public ResponseEntity<List<TradeHistory>> getByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long accountId = accountService.getPrimaryAccount().getId();
        List<TradeHistory> result = tradeHistoryService.getByDateRange(
                accountId,
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay());
        return ResponseEntity.ok(result);
    }

    /** 오늘 / 누적 손익 통계 */
    @GetMapping("/stats")
    public ResponseEntity<TradeHistoryService.TradeStats> getStats() {
        Long accountId = accountService.getPrimaryAccount().getId();
        return ResponseEntity.ok(tradeHistoryService.getStats(accountId));
    }
}
