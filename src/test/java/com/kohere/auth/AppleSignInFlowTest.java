package com.kohere.auth;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kohere.TestcontainersConfiguration;
import com.kohere.auth.domain.AppleAuthClient;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Apple 인가코드 로그인 종단 통합 테스트(@SpringBootTest + MockMvc, ADR-0031). OIDC 검증은 가짜로(id_token==subject),
 * Apple {@link AppleAuthClient} 포트는 @MockitoBean으로 대체해 실제 appleid.apple.com을 호출하지 않는다.
 * Security·JPA(MySQL Testcontainers)·Redis·이벤트는 실제 구동한다.
 *
 * <p>검증: authorizationCode 교환→반환 id_token 재검증→PENDING 생성·apple_refresh_token 저장, 그리고 탈퇴 시 매핑 삭제 전에
 * {@code /auth/revoke}가 저장된 토큰으로 호출된다. 자격 누락(authorizationCode 없음)은 400 AUTH_MISSING_CREDENTIAL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AppleSignInFlowTest {

  @TestConfiguration
  static class FakeOidcConfig {
    @Bean
    @Primary
    OidcTokenVerifier fakeOidcTokenVerifier() {
      return (provider, idToken) -> new OidcUser(provider, idToken, idToken + "@example.com");
    }
  }

  @Autowired private MockMvc mockMvc;
  @MockitoBean private AppleAuthClient appleAuthClient; // 실제 Apple HTTP 대체
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void appleSignIn_newUser_thenWithdraw_revokesRefreshToken() throws Exception {
    when(appleAuthClient.exchangeAuthorizationCode("apple-code-1"))
        .thenReturn(new AppleAuthClient.AppleTokens("apple-sub-1", "apple-rt-1"));

    // Apple 신규 로그인 → authorizationCode 교환·검증 후 PENDING + apple_refresh_token 저장
    String loginBody =
        mockMvc
            .perform(
                post("/api/v1/auth/social-login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"provider\":\"APPLE\",\"authorizationCode\":\"apple-code-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.onboardingRequired").value(true))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String onboardingToken = read(loginBody, "data", "accessToken");
    verify(appleAuthClient).exchangeAuthorizationCode("apple-code-1");

    // 탈퇴(PENDING 허용) → 매핑 삭제 전에 저장된 refresh token으로 revoke 호출(ADR-0031 #5)
    mockMvc
        .perform(
            delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(onboardingToken)))
        .andExpect(status().isNoContent());
    verify(appleAuthClient).revokeRefreshToken("apple-rt-1");
  }

  @Test
  void appleSignIn_missingAuthorizationCode_returnsMissingCredential() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/social-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"APPLE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("AUTH_MISSING_CREDENTIAL"));
  }

  private String read(String json, String... path) throws Exception {
    JsonNode node = objectMapper.readTree(json);
    for (String key : path) {
      node = node.path(key);
    }
    return node.asText();
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
