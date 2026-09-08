package com.example.cryptobot.chart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserIndicatorSettingRepository extends JpaRepository<UserIndicatorSetting, Long> {

    List<UserIndicatorSetting> findByUserIdAndSymbolOrderByCreatedAtDesc(Long userId, String symbol);

    java.util.Optional<UserIndicatorSetting> findByIdAndUserId(Long id, Long userId);
}
