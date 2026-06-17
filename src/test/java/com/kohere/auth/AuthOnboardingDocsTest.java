package com.kohere.auth;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kohere.TestcontainersConfiguration;
import com.kohere.auth.domain.OidcTokenVerifier;
import com.kohere.auth.domain.OidcUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Spring REST Docs 스니펫 생성 테스트(ADR-0007). auth-onboarding 7개 엔드포인트의 요청/응답 스니펫을 {@code
 * build/generated-snippets}에 생성한다(소셜 OIDC만 가짜 주입, Security·JPA·JWT는 실제 구동).
 *
 * <p>시나리오 회귀 검증은 {@link AuthOnboardingFlowTest}가 담당하고, 본 테스트는 문서 스니펫 생성에 집중한다.
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthOnboardingDocsTest {

  @TestConfiguration
  static class FakeOidcConfig {
    @Bean
    @Primary
    OidcTokenVerifier fakeOidcTokenVerifier() {
      return (provider, idToken) -> new OidcUser(provider, idToken, idToken + "@example.com");
    }
  }

  @Autowired private WebApplicationContext context;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp(RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .apply(documentationConfiguration(restDocumentation))
            .build();
  }

  @Test
  void generatesAuthOnboardingSnippets() throws Exception {
    // 소셜 로그인(신규) → 온보딩 임시 토큰
    String login =
        mockMvc
            .perform(
                post("/api/v1/auth/social-login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"provider\":\"GOOGLE\",\"idToken\":\"docs-sub-1\"}"))
            .andExpect(status().isOk())
            .andDo(document("auth-social-login"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String onboardingToken = read(login, "data", "accessToken");

    // 온보딩 완료 → 정식 토큰
    String onboarding =
        mockMvc
            .perform(
                post("/api/v1/auth/onboarding")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + onboardingToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(onboardingJson()))
            .andExpect(status().isOk())
            .andDo(document("auth-onboarding"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String accessToken = read(onboarding, "data", "accessToken");
    String refreshToken = read(onboarding, "data", "refreshToken");

    // 내 프로필 조회
    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andDo(document("user-get-me"));

    // 내 프로필 부분 수정
    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"marketingAgreed\":true}"))
        .andExpect(status().isOk())
        .andDo(document("user-patch-me"));

    // 재발급(회전)
    String reissue =
        mockMvc
            .perform(
                post("/api/v1/auth/reissue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isOk())
            .andDo(document("auth-reissue"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String newRefreshToken = read(reissue, "data", "refreshToken");

    // 로그아웃
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + newRefreshToken + "\"}"))
        .andExpect(status().isNoContent())
        .andDo(document("auth-logout"));

    // 탈퇴
    mockMvc
        .perform(
            delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isNoContent())
        .andDo(document("user-withdraw"));
  }

  private String read(String json, String... path) throws Exception {
    JsonNode node = objectMapper.readTree(json);
    for (String key : path) {
      node = node.path(key);
    }
    return node.asText();
  }

  private static String onboardingJson() {
    return """
        {
          "firstName": "Gil",
          "lastName": "Hong",
          "gender": "MALE",
          "birthDate": "1990-01-01",
          "countryCode": "+82",
          "phoneNumber": "1012345678",
          "visaType": "VISA_WORK",
          "termsOfServiceAgreed": true,
          "privacyPolicyAgreed": true,
          "marketingAgreed": false
        }
        """;
  }
}
