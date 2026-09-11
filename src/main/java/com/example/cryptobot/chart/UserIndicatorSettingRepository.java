package com.example.cryptobot.chart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserIndicatorSettingRepository extends JpaRepository<UserIndicatorSetting, Long> {

    List<UserIndicatorSetting> findByUserIdAndSymbolOrderByCreatedAtDesc(Long userId, String symbol);

    java.util.Optional<UserIndicatorSetting> findByIdAndUserId(Long id, Long userId);

    /** FREE 티어 게이트용: 해당 회원의 전체 지표 개수 */
    long countByUserId(Long userId);
}
