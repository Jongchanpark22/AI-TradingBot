package com.example.cryptobot.strategy.backtest;

import com.example.cryptobot.strategy.scanner.MarketScannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 백테스트 기반 ML 학습 데이터 대량 생성 엔드포인트.
 *
 * <pre>
 * POST /admin/backtest/generate
 * {
 *   "symbols": ["KRW-BTC", "KRW-ETH"],  // 비어있으면 거래대금 상위 30개 자동 선택
 *   "monthsBack": 24                     // 기본 24개월
 * }
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/admin/backtest")
@RequiredArgsConstructor
public class BacktestGenerationController {

    private final BacktestDataGenerationService generationService;
    private final MarketScannerService marketScannerService;

    /**
     * ML 학습 데이터 생성 실행.
     *
     * <pre>
     * {
     *   "symbols": ["KRW-BTC"],  // 비워두면 거래대금 상위 30개 자동
     *   "monthsBack": 24,
     *   "independentMode": true  // true: 6개 전략 독립 실행 (메타라벨링용)
     * }
     * </pre>
     */
    @PostMapping("/generate")
    public ResponseEntity<BacktestDataGenerationService.GenerationResult> generate(
            @RequestBody(required = false) GenerateRequest request) {

        if (request == null) request = new GenerateRequest(null, 0, true);

        // 심볼 미지정 시 거래대금 기준으로만 선택 (백테스트: 당일 변동률 필터 제외 — 날마다 일관된 유니버스 유지)
        List<String> symbols = (request.symbols() == null || request.symbols().isEmpty())
                ? marketScannerService.scanTopByVolumeOnly()
                : request.symbols();
        int months = request.monthsBack() > 0 ? request.monthsBack() : 24;
        boolean independent = Boolean.TRUE.equals(request.independentMode());

        log.info("[BacktestGen] 시작 — 심볼 {}개, 기간 {}개월, 모드={}",
                symbols.size(), months, independent ? "독립(6전략)" : "HYBRID");

        BacktestDataGenerationService.GenerationResult result = independent
                ? generationService.generateIndependent(symbols, months)
                : generationService.generate(symbols, months);
        return ResponseEntity.ok(result);
    }

    /**
     * source='BACKTEST' 데이터만 삭제. LIVE/PAPER 데이터는 유지.
     */
    @DeleteMapping("/clear")
    public ResponseEntity<String> clearBacktestData() {
        log.info("[BacktestGen] BACKTEST 데이터 삭제 요청");
        generationService.clearBacktestData();
        return ResponseEntity.ok("BACKTEST 데이터 삭제 완료 (LIVE/PAPER 유지)");
    }

    public record GenerateRequest(List<String> symbols, int monthsBack, Boolean independentMode) {}
}
