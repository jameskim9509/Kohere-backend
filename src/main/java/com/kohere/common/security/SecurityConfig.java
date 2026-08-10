package com.kohere.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 보안 필터 체인 구성(ADR-0010). 무상태 Bearer 인증 — 세션·CSRF·폼로그인 비활성, {@link JwtAuthenticationFilter}를 인증 필터
 * 앞에 등록한다.
 *
 * <p>보호 경로: (1) 공개(permitAll) — social-login·reissue·actuator health·매물 탐색, (2) 온보딩 스코프 이상 —
 * onboarding·DELETE /users/me(PENDING 탈퇴 허용), (3) 정식 인증(ROLE_USER) — GET/PATCH
 * /users/me·logout·찜·최근 본 매물 등, (4) <b>게스트 허용(permitAll)</b> — 퀴즈·생활 팁·v2 진단(회원·게스트가 함께 닿는다).
 * PENDING(ROLE_ONBOARDING) 토큰으로 ROLE_USER 자원 접근 시 {@link RestAccessDeniedHandler}가 403
 * AUTH_ONBOARDING_REQUIRED로 응답한다.
 *
 * <p>(4)는 토큰이 오면 {@link JwtAuthenticationFilter}가 세운 주체를 그대로 쓰고, 없으면 주체 없이(userId=null) 통과시킨다(게스트
 * 신원 표현은 {@link AuthPrincipals#userIdOrNull}). <b>만료 토큰은 게스트로 강등하지 않고 기본 401 TOKEN_EXPIRED</b>이며,
 * 신원이 무관한 공개 티어({@link PublicPaths} — 로그인·재발급·health·문서)만 예외로 통과시킨다 — 재발급 요청에 만료된 access 토큰이 실려 와도
 * 막히지 않게 하기 위해서다. 매물 탐색·퀴즈·생활 팁·진단은 PublicPaths가 아니므로 만료 토큰이면 401이다(#181).
 *
 * <p>매처는 선언 순서대로 평가되므로 게스트 허용 줄은 반드시 {@code anyRequest()} 위에 있어야 한다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final RestAuthenticationEntryPoint authenticationEntryPoint;
  private final RestAccessDeniedHandler accessDeniedHandler;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // (1) 공개
                    .requestMatchers(
                        HttpMethod.POST, "/api/v1/auth/social-login", "/api/v1/auth/reissue")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/swagger-ui/**")
                    .permitAll()
                    // 매물 탐색은 가입 전에도 사용할 수 있는 공개 기능이다. HTTP method를 GET으로 한정하고 한 단계
                    // 하위 경로만 열어 /{listingId}/favorite·/{listingId}/bookings 같은 사용자 액션은 공개하지 않는다.
                    // /listings/*는 현재 map·search·places·{listingId} 상세 조회를 포함한다.
                    .requestMatchers(HttpMethod.GET, "/api/v1/listings", "/api/v1/listings/*")
                    .permitAll()
                    // (2) 온보딩 스코프 이상 허용 — 약관 동의·연락처 인증·온보딩 흐름(PENDING/TERMS_AGREED 토큰 허용).
                    // 세입자 이메일 인증(/auth/email/**)은 #192에서 온보딩 흐름에서 제외돼 정식(ACTIVE) 전용으로 반전 → (3)으로
                    // 이동.
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/terms",
                        "/api/v1/auth/phone/verification-code",
                        "/api/v1/auth/phone/verify",
                        "/api/v1/auth/onboarding",
                        "/api/v1/auth/landlord/onboarding")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/users/me")
                    .authenticated()
                    // (3) 정식 인증(ROLE_USER) — 온보딩 완료(ACTIVE) 사용자만. 사업자번호 검증은 온보딩 후 임대인이
                    // 호출(ADR-0033).
                    // 세입자 이메일 인증은 온보딩 완료 후 호출하는 API라 정식 토큰 전용이다(#192).
                    .requestMatchers("/api/v1/users/me")
                    .hasRole("USER")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/email/verification-code",
                        "/api/v1/auth/email/verify",
                        "/api/v1/auth/business/verify",
                        "/api/v1/auth/logout")
                    .hasRole("USER")
                    // 찜과 최근 본 매물은 사용자별 데이터를 읽고 변경하므로 ACTIVE(ROLE_USER) 사용자만 허용한다.
                    // 명시하지 않고 anyRequest().authenticated()에 맡기면 ROLE_ONBOARDING 토큰도 통과할 수 있다.
                    .requestMatchers(HttpMethod.POST, "/api/v1/listings/*/favorite")
                    .hasRole("USER")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/listings/*/favorite")
                    .hasRole("USER")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/users/me/favorites",
                        "/api/v1/users/me/recent-listings")
                    .hasRole("USER")
                    // 매물 예약(신청) — ACTIVE(ROLE_USER) 세입자만. TENANT 여부는 서비스에서 재검사한다.
                    .requestMatchers(HttpMethod.POST, "/api/v1/listings/*/bookings")
                    .hasRole("USER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/bookings", "/api/v1/bookings/*")
                    .hasRole("USER")
                    // 예약 내역 관리(삭제·차단·신고) — 조회 매처는 GET·단일 세그먼트라 신규 경로를 덮지 못한다.
                    // 명시하지 않으면 anyRequest().authenticated()로 떨어져 온보딩(ROLE_ONBOARDING) 토큰이 통과한다.
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/bookings/*")
                    .hasRole("USER")
                    .requestMatchers(
                        HttpMethod.POST, "/api/v1/bookings/*/block", "/api/v1/bookings/*/report")
                    .hasRole("USER")
                    // 차단 목록·해제 — /api/v1/users/me 정확 매처가 /me/blocks를 덮지 않아 별도 매처가 필요하다.
                    .requestMatchers("/api/v1/users/me/blocks", "/api/v1/users/me/blocks/*")
                    .hasRole("USER")
                    // (4) 게스트 허용(permitAll) — 회원·비회원이 함께 닿는다(#181). 만료 토큰이 조용히 게스트로 강등되지
                    // 않게 막는 가드는 JwtAuthenticationFilter에 있다(공개 티어 PublicPaths만 예외).
                    // 생활 팁 — 비회원 허용(US-8·08-life-tips.md). 게스트는 users 행이 없어 표시 언어를 en으로 고정하고,
                    // 세입자 한정 게이트는 제거해 임대인도 조회할 수 있다.
                    .requestMatchers("/api/v1/life-tips/**")
                    .permitAll()
                    // 학습 퀴즈 — 비회원 허용(06-gamification.md). 역할 게이트 없이 누구나 풀 수 있고, 포인트 적립만
                    // 회원(userId != null)에게 적용한다.
                    .requestMatchers("/api/v1/quizzes/**")
                    .permitAll()
                    // v2 서버 주도 진단 — 비회원 허용(US-2-7). v1 진단(/api/v1/diagnoses/**)은 매처를 추가하지 않고
                    // anyRequest().authenticated()에 남겨 회원 전용으로 유지한다.
                    .requestMatchers("/api/v2/diagnoses/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
