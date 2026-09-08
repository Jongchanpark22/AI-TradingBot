package com.example.cryptobot.report;

import com.example.cryptobot.report.dto.ReportSubmitResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.List;
import java.util.Optional;

/**
 * AI 기업 리포트 서비스.
 *
 * <p>비동기 생성 흐름:
 * <ol>
 *   <li>submitReport() → SavedReport(GENERATING) 생성 후 즉시 반환 (수 ms)</li>
 *   <li>ReportAsyncTask.generate() → 백그라운드에서 DART + Gemini 처리</li>
 *   <li>완료: status=DONE / 실패: status=FAILED + errorMessage</li>
 * </ol>
 * 같은 corpCode+year의 DONE 리포트가 있으면 Gemini 재호출 없이 재사용합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportService {

    private final SavedReportRepository savedReportRepository;
    private final ReportAsyncTask reportAsyncTask;

    // ─── 생성 요청 (즉시 반환) ─────────────────────────────────────────────────

    /**
     * 리포트 생성 요청 — 직전 사업연도 자동 선택.
     *
     * @param userId   요청 회원 ID
     * @param corpCode DART 기업 고유번호 8자리
     */
    public ReportSubmitResponse submitReport(Long userId, String corpCode) {
        return submitReport(userId, corpCode, Year.now().getValue() - 1);
    }

    /**
     * 리포트 생성 요청 — 사업연도 지정.
     *
     * <ul>
     *   <li>GENERATING 중인 동일 리포트 → 같은 ID 반환 (더블클릭 방지)</li>
     *   <li>DONE 리포트 존재 → 재생성 없이 같은 ID 반환 (Gemini 비용 절약)</li>
     *   <li>없으면 → GENERATING 레코드 생성 + 비동기 작업 트리거</li>
     * </ul>
     *
     * @param userId       요청 회원 ID
     * @param corpCode     DART 기업 고유번호 8자리
     * @param businessYear 사업연도
     */
    public ReportSubmitResponse submitReport(Long userId, String corpCode, int businessYear) {
        log.info("[리포트] 요청 수신: userId={}, corpCode={}, year={}, 호출스레드={}",
                userId, corpCode, businessYear, Thread.currentThread().getName());

        // 1. 이미 생성 중이면 같은 ID 반환 (async 재호출 없음)
        Optional<SavedReport> generating = savedReportRepository
                .findFirstByUserIdAndTargetCodeAndBusinessYearAndStatusOrderByCreatedAtDesc(
                        userId, corpCode, businessYear, SavedReport.Status.GENERATING);
        if (generating.isPresent()) {
            log.info("[리포트] 이미 생성 중인 리포트 발견 — async 재호출 없이 기존 ID 반환: reportId={}", generating.get().getId());
            return new ReportSubmitResponse(generating.get().getId(), SavedReport.Status.GENERATING.name());
        }

        // 2. 완료된 리포트가 있으면 재생성 없이 반환
        Optional<SavedReport> done = savedReportRepository
                .findFirstByUserIdAndTargetCodeAndBusinessYearAndStatusOrderByCreatedAtDesc(
                        userId, corpCode, businessYear, SavedReport.Status.DONE);
        if (done.isPresent()) {
            log.info("[리포트] 완료된 리포트 재사용 — Gemini 재호출 없음: reportId={}", done.get().getId());
            return new ReportSubmitResponse(done.get().getId(), SavedReport.Status.DONE.name());
        }

        // 3. GENERATING 레코드 즉시 커밋 (비동기 작업이 findById로 조회할 수 있어야 함)
        SavedReport report = savedReportRepository.save(SavedReport.builder()
                .userId(userId)
                .targetType(SavedReport.TargetType.STOCK)
                .targetCode(corpCode)
                .businessYear(businessYear)
                .status(SavedReport.Status.GENERATING)
                .build());
        log.info("[리포트] GENERATING 레코드 생성: reportId={}", report.getId());

        // 4. 백그라운드 생성 트리거
        log.info("[리포트] 비동기 태스크 제출: reportId={}", report.getId());
        reportAsyncTask.generate(report.getId(), corpCode, businessYear);
        log.info("[리포트] 비동기 태스크 제출 완료 — 이후 처리는 report-async 스레드에서 진행");
        return new ReportSubmitResponse(report.getId(), SavedReport.Status.GENERATING.name());
    }

    // ─── 조회 ─────────────────────────────────────────────────────────────────

    /**
     * 보관함 목록 (상태 포함).
     *
     * @param userId 요청 회원 ID
     */
    public List<SavedReport> listSavedReports(Long userId) {
        return savedReportRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 보관함 단건 조회 (폴링용).
     * GENERATING → narrative null | DONE → 전체 | FAILED → errorMessage
     */
    public Optional<SavedReport> getSavedReport(Long id) {
        return savedReportRepository.findById(id);
    }
}
