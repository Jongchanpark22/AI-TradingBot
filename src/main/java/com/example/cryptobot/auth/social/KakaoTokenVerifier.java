package com.example.cryptobot.auth.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * 카카오 access_token 서버 검증기.
 *
 * <p>앱이 카카오 SDK로 발급받은 access_token 을
 * 카카오 사용자 정보 API(kapi.kakao.com/v2/user/me)로 검증합니다.
 * 위조 토큰 방지를 위해 반드시 서버에서 제공자 API를 직접 호출합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoTokenVerifier {

    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestTemplate upbitRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${social.kakao.rest-api-key:}")
    private String restApiKey;

    /**
     * 카카오 access_token 을 검증하고 사용자 정보를 반환합니다.
     *
     * @param accessToken 앱에서 카카오 SDK로 획득한 access_token
     * @return 검증된 사용자 정보
     * @throws IllegalArgumentException 토큰이 유효하지 않은 경우
     */
    public SocialUserInfo verify(String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            ResponseEntity<String> response = upbitRestTemplate.exchange(
                    KAKAO_USER_INFO_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());

            String providerUserId = root.path("id").asText();
            String email = root.path("kakao_account").path("email").asText(null);
            String nickname = root.path("kakao_account").path("profile").path("nickname").asText(null);

            if (providerUserId.isBlank()) {
                throw new IllegalArgumentException("카카오 토큰 검증 실패: 사용자 ID 없음");
            }

            log.info("[Kakao] 토큰 검증 성공: providerUserId={}", providerUserId);
            return new SocialUserInfo(providerUserId, email, nickname);

        } catch (HttpClientErrorException e) {
            log.warn("[Kakao] 토큰 검증 실패: status={}", e.getStatusCode());
            throw new IllegalArgumentException("유효하지 않은 카카오 토큰입니다.");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Kakao] 토큰 검증 오류", e);
            throw new IllegalArgumentException("카카오 인증 중 오류가 발생했습니다.");
        }
    }
}
