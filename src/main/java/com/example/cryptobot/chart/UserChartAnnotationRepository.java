package com.example.cryptobot.chart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserChartAnnotationRepository extends JpaRepository<UserChartAnnotation, Long> {

    List<UserChartAnnotation> findByUserIdAndSymbolOrderByCreatedAtDesc(Long userId, String symbol);

    java.util.Optional<UserChartAnnotation> findByIdAndUserId(Long id, Long userId);
}
