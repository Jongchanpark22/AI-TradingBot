package com.example.cryptobot.report;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 기업 리포트 API.
 *
 * <p>DART 재무 데이터 기반 AI 서술 리포트를 온디맨드로 생성합니다.
 * 코인이 아닌 국내 주식 기업 분석용이며, DART corp_code 입력이 필요합니다.</p>
 *
 * <p>⚠️ 면책: 목표주가·매수의견을 제공하지 않으며 투자 권유가 아닙니다.</p>
 */
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class AiReportController {

    private final AiReportService aiReportService;

    /**
     * 기업 리포트 생성 (직전 사업연도 자동 선택).
     *
     * @param corpCode DART 기업 고유번호 8자리 (예: 00126380 = 삼성전자)
     */
    @GetMapping("/company/{corpCode}")
    public ResponseEntity<AiReportResponse> getReport(@PathVariable String corpCode) {
        return aiReportService.generateReport(corpCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 특정 사업연도 기업 리포트 생성.
     *
     * @param corpCode     DART 기업 고유번호 8자리
     * @param businessYear 사업연도 (예: 2023)
     */
    @GetMapping("/company/{corpCode}/{businessYear}")
    public ResponseEntity<AiReportResponse> getReportByYear(
            @PathVariable String corpCode,
            @PathVariable int businessYear) {
        return aiReportService.generateReport(corpCode, businessYear)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 내 리포트 보관함 목록 조회.
     * 3차 인증 구현 전까지 userId=1L 고정.
     */
    @GetMapping("/saved")
    public List<SavedReport> listSaved() {
        return aiReportService.listSavedReports();
    }

    /**
     * 보관함 단건 조회.
     *
     * @param id 저장된 리포트 ID
     */
    @GetMapping("/saved/{id}")
    public ResponseEntity<SavedReport> getSaved(@PathVariable Long id) {
        return aiReportService.getSavedReport(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
