package com.kohere.auth;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kohere.TestcontainersConfiguration;
import com.kohere.auth.domain.OidcTokenVerifier;
import com.kohere.auth.domain.OidcUser;
import com.kohere.auth.domain.VerificationEmailSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
 * 로그인 이후 온보딩 여정 종단 통합 테스트(@SpringBootTest + MockMvc). Google 로그인으로 PENDING 토큰을 얻은 뒤(진입 셋업) 약관
 * 동의→이메일 인증→온보딩(ACTIVE)→프로필→재발급→탈퇴까지의 흐름을 검증한다. 소셜 OIDC 검증만 가짜로 주입(idToken==subject)하고 메일 발송은
 * 모킹(인증번호 캡처)하며 Security·JPA(MySQL/Testcontainers)·Redis·JWT·이벤트는 실제로 구동한다(스키마는 Flyway, ADR-0008).
 * provider별 sign-in 자체는 {@link GoogleSignInFlowTest}·{@link AppleSignInFlowTest}가 담당한다.
 *
 * <p>흐름: 소셜로그인(신규)→온보딩 미완료 차단(403)→약관 동의(TERMS_AGREED)→이메일 인증(코드 발송·확인)→온보딩(ACTIVE)→프로필
 * 조회·수정→재발급→재사용 탐지(401)→탈퇴(204)→탈퇴 후 조회(404)→재가입 분리.
 *
 * <p>occupation은 온보딩 **선택** 입력(#187) — 미전송/null 명시(200 ACTIVE + 응답 생략)·빈 문자열(400 INVALID_INPUT)
 * 케이스는 별도 테스트로 검증한다(US-1-2 AC).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OnboardingFlowTest {

  @TestConfiguration
  static class FakeOidcConfig {
    @Bean
    @Primary
    OidcTokenVerifier fakeOidcTokenVerifier() {
      return (provider, idToken) -> new OidcUser(provider, idToken, idToken + "@example.com");
    }
  }

  @Autowired private MockMvc mockMvc;
  @MockitoBean private VerificationEmailSender emailSender; // 실제 SMTP 발송 대체(인증번호 캡처)
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void fullScenario() throws Exception {
    String email = "gil@example.com";

    // 1) 소셜 로그인 (신규) → 온보딩 임시 토큰(refresh 없음), status=PENDING
    String loginBody =
        mockMvc
            .perform(
                post("/api/v1/auth/social-login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"provider\":\"GOOGLE\",\"idToken\":\"google-sub-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.onboardingRequired").value(true))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String onboardingToken = read(loginBody, "data", "accessToken");

    // 2) 온보딩 미완료(PENDING) 토큰으로 보호 자원 접근 → 403 AUTH_ONBOARDING_REQUIRED
    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(onboardingToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH_ONBOARDING_REQUIRED"));

    // 3) 약관 동의 → TERMS_AGREED
    mockMvc
        .perform(
            post("/api/v1/auth/terms")
                .header(HttpHeaders.AUTHORIZATION, bearer(onboardingToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"termsOfServiceAgreed\":true,\"privacyPolicyAgreed\":true,\"marketingAgreed\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("TERMS_AGREED"));

    // 4) 이메일 인증번호 발송 → 발송 메일에서 인증번호 캡처
    mockMvc
        .perform(
            post("/api/v1/auth/email/verification-code")
                .header(HttpHeaders.AUTHORIZATION, bearer(onboardingToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.expiresIn").isNumber());
    ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
    verify(emailSender).send(eq(email), codeCaptor.capture());
    String code = codeCaptor.getValue();

    // 5) 이메일 인증 확인 → verified
    mockMvc
        .perform(
            post("/api/v1/auth/email/verify")
                .header(HttpHeaders.AUTHORIZATION, bearer(onboardingToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.verified").value(true));

    // 6) 온보딩 완료(TERMS_AGREED→ACTIVE) → 정식 access + refresh
    String onboardingBody =
        mockMvc
            .perform(
                post("/api/v1/auth/onboarding")
                    .header(HttpHeaders.AUTHORIZATION, bearer(onboardingToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(onboardingJson(email)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.nickname").isNotEmpty())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String accessToken = read(onboardingBody, "data", "accessToken");
    String refreshToken = read(onboardingBody, "data", "refreshToken");

    // 7) 내 프로필 조회 → ACTIVE, 국적(코드)·국기 resolve
    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.firstName").value("Gil"))
        .andExpect(jsonPath("$.data.country").value("KR"))
        .andExpect(jsonPath("$.data.countryFlag").value("https://flagcdn.com/kr.svg"));

    // 8) 프로필 부분 수정
    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Updated\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.firstName").value("Updated"));

    // 9) 재발급(항상 회전) → 새 refresh
    String reissueBody =
        mockMvc
            .perform(
                post("/api/v1/auth/reissue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String newAccessToken = read(reissueBody, "data", "accessToken");

    // 10) 회전된 옛 refresh 재제출(재사용 탐지) → 401
    mockMvc
        .perform(
            post("/api/v1/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_REFRESH_TOKEN"));

    // 11) 탈퇴 → 204
    mockMvc
        .perform(
            delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(newAccessToken)))
        .andExpect(status().isNoContent());

    // 12) 탈퇴 후 조회 → 404 USER_NOT_FOUND
    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(newAccessToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));

    // 13) 같은 소셜 계정 재로그인 → social_accounts 삭제로 신규 PENDING 분리 재가입(ADR-0014)
    mockMvc
        .perform(
            post("/api/v1/auth/social-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"GOOGLE\",\"idToken\":\"google-sub-1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.onboardingRequired").value(true))
        .andExpect(jsonPath("$.data.status").value("PENDING"));
  }

  /**
   * US-1-2 AC(#187): occupation 미전송이어도 온보딩이 완료(ACTIVE)되고, 저장하지 않으므로(NULL) 온보딩 응답과 프로필 조회 모두에서
   * occupation 필드가 생략된다(NON_NULL 직렬화).
   */
  @Test
  void onboardingWithoutOccupation_activatesAndOmitsOccupation() throws Exception {
    String email = "occ-omitted@example.com";
    String token = readyForOnboarding("google-sub-occ-omitted", email);

    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/onboarding")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(onboardingJson(email, "")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.user.occupation").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String accessToken = read(body, "data", "accessToken");
    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.occupation").doesNotExist());
  }

  /** US-1-2 AC(#187): {@code "occupation": null} 명시 전송도 미전송과 동일하게 취급한다(200 ACTIVE + 응답 생략). */
  @Test
  void onboardingWithNullOccupation_activatesAndOmitsOccupation() throws Exception {
    String email = "occ-null@example.com";
    String token = readyForOnboarding("google-sub-occ-null", email);

    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/onboarding")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(onboardingJson(email, "\"occupation\": null,")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.user.occupation").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String accessToken = read(body, "data", "accessToken");
    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.occupation").doesNotExist());
  }

  /**
   * 계약 고정(#187): occupation은 선택이지만 값을 보낸 경우 enum 검증은 종전과 동일 — 빈 문자열 {@code ""}도 목록 밖 값이라 400
   * INVALID_INPUT이다(null과 다르게 취급, 정규화하지 않음).
   */
  @Test
  void onboardingWithEmptyOccupation_returnsInvalidInput() throws Exception {
    String email = "occ-empty@example.com";
    String token = readyForOnboarding("google-sub-occ-empty", email);

    mockMvc
        .perform(
            post("/api/v1/auth/onboarding")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(onboardingJson(email, "\"occupation\": \"\",")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void protectedEndpoint_withoutToken_returnsUnauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  /** 소셜 로그인(신규)→약관 동의→이메일 인증까지 마치고 온보딩 임시 토큰을 돌려준다(occupation 케이스 테스트의 공통 진입 셋업). */
  private String readyForOnboarding(String subject, String email) throws Exception {
    String loginBody =
        mockMvc
            .perform(
                post("/api/v1/auth/social-login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"provider\":\"GOOGLE\",\"idToken\":\"" + subject + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = read(loginBody, "data", "accessToken");

    mockMvc
        .perform(
            post("/api/v1/auth/terms")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"termsOfServiceAgreed\":true,\"privacyPolicyAgreed\":true,\"marketingAgreed\":false}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/email/verification-code")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
        .andExpect(status().isOk());
    ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
    verify(emailSender).send(eq(email), codeCaptor.capture());

    mockMvc
        .perform(
            post("/api/v1/auth/email/verify")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"" + email + "\",\"code\":\"" + codeCaptor.getValue() + "\"}"))
        .andExpect(status().isOk());
    return token;
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

  private static String onboardingJson(String email) {
    return onboardingJson(email, "\"occupation\": \"UNDERGRADUATE_STUDENT\",");
  }

  /**
   * 온보딩 요청 본문. {@code occupationEntry}는 occupation 항목 문자열 그 자체(예: {@code "occupation": null,}) —
   * 미전송 케이스는 빈 문자열을 넘긴다(#187 선택 입력 케이스 조립용).
   */
  private static String onboardingJson(String email, String occupationEntry) {
    return """
        {
          "firstName": "Gil",
          "lastName": "Hong",
          "gender": "MALE",
          "birthDate": "1990-01-01",
          "country": "KR",
          %s
          "email": "%s",
          "visaType": "SHORT_TERM_VISIT"
        }
        """
        .formatted(occupationEntry, email);
  }
}
