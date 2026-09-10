package com.example.cryptobot.fcm;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FCM 디바이스 토큰 등록 요청 DTO.
 */
@Getter
@Setter
@NoArgsConstructor
public class DeviceTokenRequest {

    /** FCM 등록 토큰 */
    @NotBlank(message = "FCM 토큰은 필수입니다.")
    private String token;

    /** 디바이스 유형 (기본값: ANDROID) */
    private DeviceToken.DeviceType deviceType = DeviceToken.DeviceType.ANDROID;
}
