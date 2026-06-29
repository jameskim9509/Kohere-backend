package com.kohere.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kohere.TestcontainersConfiguration;
import com.kohere.auth.domain.OidcTokenVerifier;
import com.kohere.auth.domain.OidcUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Google 소셜 로그인(sign-in) 종단 통합 테스트(@SpringBootTest + MockMvc). social-login 엔드포인트가 Google {@code
 * idToken} 검증 후 신규 회원을 PENDING으로 만들고 온보딩 임시 토큰을 발급하는지, 인증 없이 호출 가능한 공개 엔드포인트인지, 허용 외 provider를
 * 거부하는지 검증한다. OIDC 검증만 가짜로(id_token==subject) 주입하고 Security·JPA(MySQL Testcontainers)·Redis는 실제
 * 구동한다.
 *
 * <p>로그인 이후 온보딩 여정은 {@link OnboardingFlowTest}, Apple 인가코드 흐름은 {@link AppleSignInFlowTest}가 담당한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class GoogleSignInFlowTest {

  @TestConfiguration
  static class FakeOidcConfig {
    @Bean
    @Primary
    OidcTokenVerifier fakeOidcTokenVerifier() {
      return (provider, idToken) -> new OidcUser(provider, idToken, idToken + "@example.com");
    }
  }

  @Autowired private MockMvc mockMvc;

  @Test
  void newGoogleUser_isPublic_returnsPendingOnboardingToken() throws Exception {
    // 인증 헤더 없이 호출(공개 엔드포인트) → 신규 회원 PENDING + 온보딩 임시 토큰(refresh 없음)
    mockMvc
        .perform(
            post("/api/v1/auth/social-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"GOOGLE\",\"idToken\":\"google-signin-1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.onboardingRequired").value(true))
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.refreshToken").isEmpty());
  }

  @Test
  void invalidProviderEnum_isRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/social-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"FACEBOOK\",\"idToken\":\"x\"}"))
        .andExpect(status().isBadRequest());
  }
}
