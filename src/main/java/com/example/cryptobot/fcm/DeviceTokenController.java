package com.example.cryptobot.fcm;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * FCM 디바이스 토큰 관리 API.
 * 앱 기동 시 FCM 토큰을 등록·갱신하고, 로그아웃 시 삭제합니다.
 */
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * FCM 토큰 등록 또는 갱신.
     *
     * <p>동일 토큰이 이미 존재하면 userId를 현재 로그인 사용자로 업데이트합니다.
     * (앱 재설치·계정 전환 대응)</p>
     *
     * body 예: {"token":"fcm_token_here","deviceType":"ANDROID"}
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public DeviceToken registerToken(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody DeviceTokenRequest request) {

        DeviceToken device = deviceTokenRepository.findByToken(request.getToken())
                .orElseGet(() -> DeviceToken.builder()
                        .token(request.getToken())
                        .deviceType(request.getDeviceType())
                        .build());

        // 소유자 업데이트 (앱 재설치·계정 전환 대응)
        device.setUserId(userId);
        device.setDeviceType(request.getDeviceType());

        return deviceTokenRepository.save(device);
    }

    /**
     * FCM 토큰 삭제 (로그아웃·앱 삭제 시 호출).
     */
    @DeleteMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void removeToken(
            @AuthenticationPrincipal Long userId,
            @PathVariable String token) {
        deviceTokenRepository.findByToken(token)
                .filter(d -> d.getUserId().equals(userId))
                .ifPresent(deviceTokenRepository::delete);
    }
}
