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
}
