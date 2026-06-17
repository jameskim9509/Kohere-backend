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
 * <p>보호 경로 3티어: (1) 공개(permitAll) — social-login·reissue·actuator health, (2) 온보딩 스코프 이상 —
 * onboarding·DELETE /users/me(PENDING 탈퇴 허용), (3) 정식 인증(ROLE_USER) — GET/PATCH /users/me·logout 등.
 * PENDING(ROLE_ONBOARDING) 토큰으로 ROLE_USER 자원 접근 시 {@link RestAccessDeniedHandler}가 403
 * AUTH_ONBOARDING_REQUIRED로 응답한다.
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
                    .requestMatchers("/actuator/health", "/docs/**", "/swagger-ui/**")
                    .permitAll()
                    // (2) 온보딩 스코프 이상 허용
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/onboarding")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/users/me")
                    .authenticated()
                    // (3) 정식 인증(ROLE_USER)
                    .requestMatchers("/api/v1/users/me")
                    .hasRole("USER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                    .hasRole("USER")
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
