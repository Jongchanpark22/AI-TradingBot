package com.example.cryptobot.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAlertRepository extends JpaRepository<UserAlert, Long> {

    List<UserAlert> findAllByEnabledTrue();

    List<UserAlert> findAllBySymbolAndEnabledTrue(String symbol);

    List<UserAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    java.util.Optional<UserAlert> findByIdAndUserId(Long id, Long userId);
}
