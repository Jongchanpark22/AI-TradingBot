package com.example.cryptobot.common.config;

import com.example.cryptobot.auth.exception.AuthFailureHandler;
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
 * <p>엔드포인트 권한 맵:
 * <ul>
 *   <li>관리자 전용: /api/admin/** — ROLE_ADMIN 보유 사용자만 접근</li>
 *   <li>공개: /api/auth/**, Swagger, Actuator, /api/health</li>
 *   <li>공개(선택): GET /api/news/**, GET /api/market/**, GET /api/coins/** — 로그인 없이 열람 허용</li>
 *   <li>나머지: 인증 필요 (JWT 필수)</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final AuthFailureHandler authFailureHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        // 미인증 → 401 ApiResponse 봉투
                        .authenticationEntryPoint(authFailureHandler)
                        // 권한 부족 → 403 ApiResponse 봉투
                        .accessDeniedHandler(authFailureHandler)
                )
                .authorizeHttpRequests(authz -> authz
                        // 관리자 전용
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 로그아웃은 인증 필요 — /api/auth/** 보다 먼저 선언
                        .requestMatchers("/api/auth/logout").authenticated()
                        // 공개 — 인증 불필요
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        // 뉴스·시세·차트 데이터는 공개 열람 허용
                        .requestMatchers(HttpMethod.GET, "/api/news/**", "/news/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/market/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/coins/**").permitAll()
                        // 나머지 전부 인증 필요
                        .anyRequest().authenticated()
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
