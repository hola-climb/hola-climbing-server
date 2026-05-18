package com.holaclimbing.server.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 메인 설정.
 *
 * 개발 친화적 정책:
 * - CSRF off (REST API라 불필요)
 * - Session stateless (JWT 기반)
 * - JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 등록
 * - 401/403 핸들러는 공통 ApiResponse 포맷으로 응답
 * - 현재는 .anyRequest().permitAll() → 개발 편의 최대화
 * - 발표 직전 → .authenticated()로 강화 + 필요한 API에 @PreAuthorize
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /** BCrypt 워크 팩터. 메모리: strength=12 */
    private static final int BCRYPT_STRENGTH = 12;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint entryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // 인증 관련 공개 API (회원가입/로그인/토큰재발급/이메일인증/중복확인)
                        .requestMatchers("/api/auth/**").permitAll()
                        // 문서/모니터링
                        .requestMatchers("/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v1/api-docs/**",
                                "/actuator/**",
                                "/api/docs/**").permitAll()
                        // WebSocket 핸드셰이크 — 인증은 StompHandshakeInterceptor가 담당
                        .requestMatchers("/ws/**").permitAll()
                        // 공개 조회 (영상 피드, 암장 검색 등)
                        .requestMatchers(HttpMethod.GET,
                                "/api/videos/**",
                                "/api/gyms/**").permitAll()
                        // 본인 인증이 필요한 회원 API (내 프로필, 팔로우/차단 변경)
                        .requestMatchers("/api/users/me", "/api/users/me/**").authenticated()
                        .requestMatchers(HttpMethod.POST,
                                "/api/users/*/follow",
                                "/api/users/*/block").authenticated()
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/users/*/follow",
                                "/api/users/*/block").authenticated()
                        // 즐겨찾기 — 본인 전용
                        .requestMatchers("/api/favorites/**").authenticated()
                        // 알림 — 본인 전용
                        .requestMatchers("/api/notifications/**").authenticated()
                        // 신고 — 등록은 인증 필요
                        .requestMatchers(HttpMethod.POST, "/api/reports").authenticated()
                        // 약관 — 조회는 공개, 동의 기록은 인증 필요
                        .requestMatchers(HttpMethod.POST, "/api/terms/agree").authenticated()
                        // 채팅 REST — 본인 전용
                        .requestMatchers("/api/chats/**").authenticated()
                        // 통계 — 내 통계는 인증 필요 (특정 사용자 통계는 공개)
                        .requestMatchers(HttpMethod.GET, "/api/stats/me", "/api/stats/me/**").authenticated()
                        // 추천 — 본인 홈 피드
                        .requestMatchers("/api/recommendations/**").authenticated()
                        // AI 분석 — 조회 공개 + 결과 수신은 AI 워커 서버 간 콜백
                        // TODO(release): 결과 수신(POST)에 워커 공유 시크릿 인증 추가
                        .requestMatchers("/api/analysis/**").permitAll()
                        // 영상 등록·수정·삭제·좋아요·댓글 — 인증 필요 (GET 피드/상세/댓글목록은 위에서 공개)
                        .requestMatchers(HttpMethod.POST, "/api/videos", "/api/videos/**").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/videos/**", "/api/comments/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/videos/**", "/api/comments/**").authenticated()
                        // 개발 단계: 그 외도 일단 다 통과
                        // TODO(release): 아래 줄을 .authenticated()로 바꾸고 보호 필요 API에 @PreAuthorize
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}