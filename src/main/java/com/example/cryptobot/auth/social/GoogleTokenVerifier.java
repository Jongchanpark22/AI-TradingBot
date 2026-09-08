package com.example.cryptobot.auth.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 구글 ID 토큰 서버 검증기.
 *
 * <p>앱이 구글 Sign-In SDK로 발급받은 ID 토큰을
 * 구글 tokeninfo 엔드포인트로 검증합니다.
 * aud 클레임이 우리 클라이언트 ID와 일치하는지 반드시 확인합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleTokenVerifier {

    private static final String GOOGLE_TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    private final RestTemplate upbitRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${social.google.client-id:}")
    private String clientId;

    /**
     * 구글 ID 토큰을 검증하고 사용자 정보를 반환합니다.
     *
     * @param idToken 앱에서 구글 Sign-In으로 획득한 ID 토큰
     * @return 검증된 사용자 정보
     * @throws IllegalArgumentException 토큰이 유효하지 않거나 aud 불일치
     */
    public SocialUserInfo verify(String idToken) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(GOOGLE_TOKENINFO_URL)
                    .queryParam("id_token", idToken)
                    .build(true)
                    .toUri();

            String body = upbitRestTemplate.getForObject(uri, String.class);
            JsonNode root = objectMapper.readTree(body);

            // aud 검증 — 우리 클라이언트 ID와 일치해야 토큰 위조 방지
            String aud = root.path("aud").asText();
            if (!clientId.isBlank() && !clientId.equals(aud)) {
                log.warn("[Google] aud 불일치: expected={}, actual={}", clientId, aud);
                throw new IllegalArgumentException("유효하지 않은 구글 토큰입니다. (aud 불일치)");
            }

            String providerUserId = root.path("sub").asText();
            String email = root.path("email").asText(null);
            String nickname = root.path("name").asText(null);

            if (providerUserId.isBlank()) {
                throw new IllegalArgumentException("구글 토큰 검증 실패: sub 없음");
            }

            log.info("[Google] 토큰 검증 성공: providerUserId={}", providerUserId);
            return new SocialUserInfo(providerUserId, email, nickname);

        } catch (HttpClientErrorException e) {
            log.warn("[Google] 토큰 검증 실패: status={}", e.getStatusCode());
            throw new IllegalArgumentException("유효하지 않은 구글 토큰입니다.");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Google] 토큰 검증 오류", e);
            throw new IllegalArgumentException("구글 인증 중 오류가 발생했습니다.");
        }
    }
}
