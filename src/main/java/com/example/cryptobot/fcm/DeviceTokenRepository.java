package com.example.cryptobot.fcm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    /** 특정 FCM 토큰 조회 */
    Optional<DeviceToken> findByToken(String token);

    /** 특정 회원의 모든 디바이스 토큰 조회 */
    List<DeviceToken> findByUserId(Long userId);

    /** 만료된 토큰 삭제 */
    void deleteByToken(String token);
}
