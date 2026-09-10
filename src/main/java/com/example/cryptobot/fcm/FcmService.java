package com.example.cryptobot.fcm;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * FCM 푸시 발송 서비스.
 *
 * <p>Firebase Admin SDK가 초기화되지 않은 경우(서비스 계정 없음) 발송을 건너뜁니다.
 * 만료된 토큰(UNREGISTERED)은 발송 후 자동으로 DB에서 삭제합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * 특정 회원의 모든 디바이스에 푸시를 발송합니다.
     *
     * @param userId 수신자 회원 ID
     * @param title  알림 제목
     * @param body   알림 본문
     */
    @Transactional
    public void sendToUser(Long userId, String title, String body) {
        if (!isFirebaseReady()) {
            log.debug("[FCM] Firebase 미초기화 — 푸시 발송 건너뜀: userId={}", userId);
            return;
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.debug("[FCM] 등록된 디바이스 없음: userId={}", userId);
            return;
        }

        for (DeviceToken device : tokens) {
            sendSingle(device, title, body);
        }
    }

    /**
     * 단일 디바이스에 푸시를 발송합니다.
     * UNREGISTERED 오류 발생 시 해당 토큰을 DB에서 삭제합니다.
     */
    private void sendSingle(DeviceToken device, String title, String body) {
        try {
            Message message = Message.builder()
                    .setToken(device.getToken())
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            String messageId = FirebaseMessaging.getInstance().send(message);
            log.info("[FCM] 발송 완료: userId={}, deviceType={}, messageId={}",
                    device.getUserId(), device.getDeviceType(), messageId);

        } catch (FirebaseMessagingException e) {
            if (MessagingErrorCode.UNREGISTERED.equals(e.getMessagingErrorCode())) {
                log.info("[FCM] 만료된 토큰 삭제: userId={}, token={}",
                        device.getUserId(), device.getToken());
                deviceTokenRepository.deleteByToken(device.getToken());
            } else {
                log.error("[FCM] 발송 실패: userId={}, errorCode={}",
                        device.getUserId(), e.getMessagingErrorCode(), e);
            }
        }
    }

    private boolean isFirebaseReady() {
        return !FirebaseApp.getApps().isEmpty();
    }
}
