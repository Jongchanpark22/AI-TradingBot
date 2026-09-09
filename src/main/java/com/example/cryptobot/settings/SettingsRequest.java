package com.example.cryptobot.settings;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 설정 부분 업데이트 요청 DTO.
 * null 필드는 기존 값을 유지합니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class SettingsRequest {

    /** 테마 (LIGHT | DARK | SYSTEM), null이면 변경 없음 */
    private UserSettings.Theme theme;

    /** 가격 알림 활성 여부, null이면 변경 없음 */
    private Boolean priceAlertEnabled;

    /** 뉴스 알림 활성 여부, null이면 변경 없음 */
    private Boolean newsAlertEnabled;

    /** AI 리포트 완료 알림 활성 여부, null이면 변경 없음 */
    private Boolean reportAlertEnabled;
}
