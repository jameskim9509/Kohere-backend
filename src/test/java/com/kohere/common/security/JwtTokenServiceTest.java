package com.kohere.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link JwtTokenService} 단위 테스트 — 발급↔검증 라운드트립, 온보딩 스코프 클레임, 위조 거부(ADR-0009/0011). */
class JwtTokenServiceTest {

  private JwtTokenService jwtTokenService;

  @BeforeEach
  void setUp() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret("test-secret-test-secret-test-secret-32bytes-min!!");
    properties.setIssuer("kohere");
    properties.setAccessTtlSeconds(3600);
    properties.setOnboardingTtlSeconds(1800);
    jwtTokenService = new JwtTokenService(properties);
  }

  @Test
  void accessToken_carriesOnboardingCompletedTrue() {
    String token = jwtTokenService.issueAccessToken(42L);

    AuthPrincipal principal = jwtTokenService.parse(token);

    assertThat(principal.userId()).isEqualTo(42L);
    assertThat(principal.onboardingCompleted()).isTrue();
  }

  @Test
  void onboardingToken_carriesOnboardingCompletedFalse() {
    String token = jwtTokenService.issueOnboardingToken(7L);

    AuthPrincipal principal = jwtTokenService.parse(token);

    assertThat(principal.userId()).isEqualTo(7L);
    assertThat(principal.onboardingCompleted()).isFalse();
  }

  @Test
  void parse_rejectsForgedToken() {
    assertThatThrownBy(() -> jwtTokenService.parse("not-a-valid-token"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void exposesConfiguredTtls() {
    assertThat(jwtTokenService.accessTtlSeconds()).isEqualTo(3600);
    assertThat(jwtTokenService.onboardingTtlSeconds()).isEqualTo(1800);
  }
}
