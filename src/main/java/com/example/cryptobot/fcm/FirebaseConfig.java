package com.example.cryptobot.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Firebase Admin SDK 초기화 설정.
 *
 * <p>서비스 계정 JSON 경로는 {@code firebase.credentials-path} 프로퍼티로 설정합니다.
 * 기본값: {@code secret/firebase-serviceAccount.json} (gitignore 처리됨)</p>
 *
 * <p>파일이 없으면 경고 로그만 출력하고 FCM 기능을 비활성화합니다.
 * (개발 환경에서 키 없이 기동 가능)</p>
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials-path:secret/firebase-serviceAccount.json}")
    private String credentialsPath;

    /**
     * 앱 기동 시 FirebaseApp을 초기화합니다.
     * 이미 초기화된 경우 건너뜁니다.
     */
    @PostConstruct
    public void initialize() {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.debug("FirebaseApp 이미 초기화됨 — 건너뜀");
            return;
        }

        try (InputStream serviceAccount = openCredentials()) {
            if (serviceAccount == null) {
                log.warn("[FCM] 서비스 계정 파일 없음 ({}): FCM 푸시 기능 비활성화", credentialsPath);
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("[FCM] Firebase Admin SDK 초기화 완료: {}", credentialsPath);
        } catch (IOException e) {
            log.error("[FCM] Firebase 초기화 실패: {}", e.getMessage());
        }
    }

    /**
     * 서비스 계정 파일을 스트림으로 엽니다.
     * 클래스패스 → 파일시스템 순으로 탐색합니다.
     */
    private InputStream openCredentials() throws IOException {
        // 1. 클래스패스에서 시도 (테스트·Docker 환경 대비)
        InputStream cp = getClass().getClassLoader().getResourceAsStream(credentialsPath);
        if (cp != null) return cp;

        // 2. 파일시스템 (로컬 secret/ 디렉토리)
        try {
            return new FileInputStream(credentialsPath);
        } catch (IOException e) {
            return null;
        }
    }
}
