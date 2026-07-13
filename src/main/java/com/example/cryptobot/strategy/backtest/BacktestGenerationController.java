package com.example.cryptobot.strategy.backtest;

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

    /**
     * ML 학습 데이터 생성 실행.
     *
     * <p>symbols를 비워두면 업비트 KRW 마켓 상위 30개를 자동 선택한다.
     */
    @PostMapping("/generate")
    public ResponseEntity<BacktestDataGenerationService.GenerationResult> generate(
            @RequestBody GenerateRequest request) {

        List<String> symbols = (request.symbols() == null || request.symbols().isEmpty())
                ? generationService.fetchTopKrwMarkets(30)
                : request.symbols();
        int months = request.monthsBack() > 0 ? request.monthsBack() : 24;

        log.info("[BacktestGen] 시작 — 심볼 {}개, 기간 {}개월", symbols.size(), months);
        BacktestDataGenerationService.GenerationResult result =
                generationService.generate(symbols, months);
        return ResponseEntity.ok(result);
    }

    public record GenerateRequest(List<String> symbols, int monthsBack) {}
}
