package com.example.cryptobot.report;

import com.example.cryptobot.report.dto.ReportSubmitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 기업 리포트 API.
 *
 * <p>생성 요청(POST)은 즉시 reportId를 반환하고, 실제 생성은 백그라운드에서 진행됩니다.
 * 클라이언트는 GET /report/saved/{id}를 폴링하여 status가 DONE이 되면 결과를 표시하세요.</p>
 *
 * <p>폴링 권장 간격: 2~3초. status=FAILED 시 에러 표시 + 재시도.</p>
 *
 * <p>⚠️ 면책: 목표주가·매수의견을 제공하지 않으며 투자 권유가 아닙니다.</p>
 */
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class AiReportController {

    private final AiReportService aiReportService;

    /**
     * 기업 리포트 생성 요청 (직전 사업연도 자동 선택).
     * 즉시 { reportId, status } 반환. 생성은 백그라운드 진행.
     *
     * @param corpCode DART 기업 고유번호 8자리 (예: 00126380 = 삼성전자)
     */
    @PostMapping("/company/{corpCode}")
    public ResponseEntity<ReportSubmitResponse> submitReport(
            @AuthenticationPrincipal Long userId,
            @PathVariable String corpCode) {
        return ResponseEntity.accepted().body(aiReportService.submitReport(userId, corpCode));
    }

    /**
     * 기업 리포트 생성 요청 — 사업연도 지정.
     *
     * @param corpCode     DART 기업 고유번호 8자리
     * @param businessYear 사업연도 (예: 2024)
     */
    @PostMapping("/company/{corpCode}/{businessYear}")
    public ResponseEntity<ReportSubmitResponse> submitReportByYear(
            @AuthenticationPrincipal Long userId,
            @PathVariable String corpCode,
            @PathVariable int businessYear) {
        return ResponseEntity.accepted().body(aiReportService.submitReport(userId, corpCode, businessYear));
    }

    /**
     * 내 리포트 보관함 목록 조회 (status 포함).
     */
    @GetMapping("/saved")
    public List<SavedReport> listSaved(@AuthenticationPrincipal Long userId) {
        return aiReportService.listSavedReports(userId);
    }

    /**
     * 보관함 단건 조회 — 폴링 엔드포인트.
     *
     * <ul>
     *   <li>status=GENERATING → narrative: null (계속 폴링)</li>
     *   <li>status=DONE       → narrative + contentJson 포함 전체 반환</li>
     *   <li>status=FAILED     → errorMessage 확인</li>
     * </ul>
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
