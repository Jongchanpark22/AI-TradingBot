package com.example.cryptobot.common.config;

import com.example.cryptobot.auth.jwt.JwtAuthenticationFilter;
import com.example.cryptobot.auth.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정.
 *
 * <p>엔드포인트 권한 맵 (3차 지시서 ★보강5 기준):
 * <ul>
 *   <li>공개: /api/auth/**, Swagger, Actuator health</li>
 *   <li>공개(선택): GET /api/news/** — 로그인 없이 뉴스 열람 허용</li>
 *   <li>인증 필요: /api/users/me/**</li>
 *   <li>관리자 전용: /admin/** — 다음 이슈에서 ROLE_ADMIN 적용 예정</li>
 *   <li>나머지: 인증 필요 (다음 이슈에서 세분화)</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        // 공개 — 인증 불필요
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // 공개(선택) — 뉴스 열람
                        .requestMatchers(HttpMethod.GET, "/api/news/**", "/news/**").permitAll()
                        // 인증 필요
                        .requestMatchers("/api/users/me/**").authenticated()
                        .requestMatchers("/api/auth/logout").authenticated()
                        // 나머지는 일단 모두 허용 (다음 이슈에서 세분화)
                        .anyRequest().permitAll()
                )
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
