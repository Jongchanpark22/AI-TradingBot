package com.example.cryptobot.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedReportRepository extends JpaRepository<SavedReport, Long> {

    List<SavedReport> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<SavedReport> findByUserIdAndTargetCodeAndBusinessYear(
            Long userId, String targetCode, Integer businessYear);

    /** 특정 상태의 최신 리포트 1건 조회 (중복 방지 + DONE 재사용에 사용) */
    Optional<SavedReport> findFirstByUserIdAndTargetCodeAndBusinessYearAndStatusOrderByCreatedAtDesc(
            Long userId, String targetCode, Integer businessYear, SavedReport.Status status);

    /** 단건 조회 — 소유권 검증 포함 */
    Optional<SavedReport> findByIdAndUserId(Long id, Long userId);
}
