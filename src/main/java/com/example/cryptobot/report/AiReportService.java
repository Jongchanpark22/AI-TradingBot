package com.example.cryptobot.report;

import com.example.cryptobot.auth.entity.User;
import com.example.cryptobot.auth.repository.UserRepository;
import com.example.cryptobot.common.apiPayload.ErrorCode;
import com.example.cryptobot.common.exception.BusinessException;
import com.example.cryptobot.report.dto.ReportSubmitResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
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
 * 같은 corpCode+year의 DONE 리포트가 있으면 Gemini 재호출 없이 재사용합니다.
 * FREE 티어는 월 {@code free.report-limit}회로 제한하며, 초과 시 429를 반환합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportService {

    private final SavedReportRepository savedReportRepository;
    private final ReportAsyncTask reportAsyncTask;
    private final UserRepository userRepository;

    @Value("${free.report-limit:5}")
    private int reportLimit;

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
     *   <li>GENERATING 중인 동일 리포트 → 같은 ID 반환 (더블클릭 방지, 카운트 소모 없음)</li>
     *   <li>DONE 리포트 존재 → 재생성 없이 같은 ID 반환 (Gemini 비용 절약, 카운트 소모 없음)</li>
     *   <li>FREE 티어 한도 초과 → 429</li>
     *   <li>없으면 → GENERATING 레코드 생성 + 비동기 작업 트리거</li>
     * </ul>
     *
     * @param userId       요청 회원 ID
     * @param corpCode     DART 기업 고유번호 8자리
     * @param businessYear 사업연도
     * @throws BusinessException FREE 티어 월 한도 초과 시 (ErrorCode.REPORT_LIMIT_EXCEEDED)
     */
    public ReportSubmitResponse submitReport(Long userId, String corpCode, int businessYear) {
        log.info("[리포트] 요청 수신: userId={}, corpCode={}, year={}, 호출스레드={}",
                userId, corpCode, businessYear, Thread.currentThread().getName());

        // 1. 이미 생성 중이면 같은 ID 반환 (카운트 소모 없음)
        Optional<SavedReport> generating = savedReportRepository
                .findFirstByUserIdAndTargetCodeAndBusinessYearAndStatusOrderByCreatedAtDesc(
                        userId, corpCode, businessYear, SavedReport.Status.GENERATING);
        if (generating.isPresent()) {
            log.info("[리포트] 이미 생성 중인 리포트 발견 — 기존 ID 반환: reportId={}", generating.get().getId());
            return new ReportSubmitResponse(generating.get().getId(), SavedReport.Status.GENERATING.name());
        }

        // 2. 완료된 리포트가 있으면 재생성 없이 반환 (카운트 소모 없음)
        Optional<SavedReport> done = savedReportRepository
                .findFirstByUserIdAndTargetCodeAndBusinessYearAndStatusOrderByCreatedAtDesc(
                        userId, corpCode, businessYear, SavedReport.Status.DONE);
        if (done.isPresent()) {
            log.info("[리포트] 완료된 리포트 재사용 — Gemini 재호출 없음: reportId={}", done.get().getId());
            return new ReportSubmitResponse(done.get().getId(), SavedReport.Status.DONE.name());
        }

        // 3. 티어 게이팅 — FREE 회원만 월 한도 검사
        checkAndIncrementReportCount(userId);

        // 4. GENERATING 레코드 즉시 커밋 (비동기 작업이 findById로 조회할 수 있어야 함)
        SavedReport report = savedReportRepository.save(SavedReport.builder()
                .userId(userId)
                .targetType(SavedReport.TargetType.STOCK)
                .targetCode(corpCode)
                .businessYear(businessYear)
                .status(SavedReport.Status.GENERATING)
                .build());
        log.info("[리포트] GENERATING 레코드 생성: reportId={}", report.getId());

        // 5. 백그라운드 생성 트리거
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
     * 보관함 단건 조회 (폴링용) — 소유권 검증 포함.
     * GENERATING → narrative null | DONE → 전체 | FAILED → errorMessage
     *
     * @param userId 요청 회원 ID (소유권 검증)
     * @param id     리포트 ID
     */
    public Optional<SavedReport> getSavedReport(Long userId, Long id) {
        return savedReportRepository.findByIdAndUserId(id, userId);
    }

    // ─── 내부 헬퍼 ────────────────────────────────────────────────────────────

    /**
     * FREE 티어 회원의 월 리포트 카운트를 검사하고 1 증가시킵니다.
     * PREMIUM 티어는 무제한이므로 검사를 건너뜁니다.
     *
     * @throws BusinessException 한도 초과 시 (ErrorCode.REPORT_LIMIT_EXCEEDED)
     */
    private void checkAndIncrementReportCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다: " + userId));

        if (user.getTier() != User.Tier.FREE) {
            return;
        }

        String currentMonth = YearMonth.now().toString(); // 예: "2026-09"

        // 월이 바뀌었으면 카운트 리셋
        if (!currentMonth.equals(user.getReportGenMonth())) {
            log.info("[리포트] 월 변경 감지 → 카운트 리셋: userId={}, 이전월={}, 현재월={}",
                    userId, user.getReportGenMonth(), currentMonth);
            user.setReportGenCount(0);
            user.setReportGenMonth(currentMonth);
        }

        if (user.getReportGenCount() >= reportLimit) {
            log.info("[리포트] FREE 한도 초과: userId={}, count={}/{}", userId, user.getReportGenCount(), reportLimit);
            throw new BusinessException(ErrorCode.REPORT_LIMIT_EXCEEDED);
        }

        user.setReportGenCount(user.getReportGenCount() + 1);
        userRepository.save(user);
        log.info("[리포트] 카운트 증가: userId={}, count={}/{}", userId, user.getReportGenCount(), reportLimit);
    }
}
