package com.kohere.auth;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kohere.TestcontainersConfiguration;
import com.kohere.auth.domain.InvalidSocialTokenException;
import com.kohere.auth.domain.OidcTokenVerifier;
import com.kohere.auth.domain.OidcUser;
import com.kohere.common.security.JwtProperties;
import com.kohere.common.security.JwtTokenService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
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
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Spring REST Docs 스니펫 생성 테스트(ADR-0007). auth-onboarding 7개 엔드포인트의 성공 응답과,
 * 스펙(01-auth-onboarding.md)에 정의된 주요 에러 응답을 {@code build/generated-snippets}에 생성한다(소셜 OIDC만 가짜 주입,
 * Security·JPA·JWT는 실제 구동).
 *
 * <p>각 에러 케이스는 {@code status}와 {@code error.code}를 함께 단정해 "스펙에 적힌 코드가 실제로 그 상황에서 나온다"는 것을 테스트로 검증한
 * 뒤 문서화한다. 에러 응답은 공통 래퍼(success=false·data=null·error.code/message/errors)를 {@code responseFields}로
 * 기술한다.
 *
 * <p>시나리오 회귀 검증은 {@link AuthOnboardingFlowTest}가 담당하고, 본 테스트는 문서 스니펫 생성·검증에 집중한다.
 *
 * <p>참고: 스펙의 social-login {@code 502 UPSTREAM_ERROR}/{@code 503 SERVICE_UNAVAILABLE}는 현재 구현이 모든
 * OIDC 검증 실패를 {@code 401 AUTH_INVALID_SOCIAL_TOKEN}으로 접으므로 실제 코드 경로가 없어 문서화 대상에서 제외한다.
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthOnboardingDocsTest {

  /** 검증 실패를 트리거하는 사회적 토큰 식별자(가짜 OIDC 검증기가 이 값에 한해 401을 던진다). */
  private static final String INVALID_SOCIAL_TOKEN = "invalid-social-token";

  /** JSON 파싱 자체가 불가한 본문 — MALFORMED_REQUEST(400) 유발. */
  private static final String MALFORMED_BODY = "{ \"oops\" }";

  @TestConfiguration
  static class FakeOidcConfig {
    @Bean
    @Primary
    OidcTokenVerifier fakeOidcTokenVerifier() {
      return (provider, idToken) -> {
        if (INVALID_SOCIAL_TOKEN.equals(idToken)) {
          throw new InvalidSocialTokenException();
        }
        return new OidcUser(provider, idToken, idToken + "@example.com");
      };
    }
  }

  @Autowired private WebApplicationContext context;
  @Autowired private JwtTokenService jwtTokenService;
  @Autowired private JwtProperties jwtProperties;
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
            .andDo(
                document(
                    "auth-social-login",
                    resourceDetails().summary("소셜 로그인 — idToken 검증 후 서버 토큰 발급(신규/기존 분기)"),
                    requestFields(socialLoginRequestFields()),
                    responseFields(socialLoginResponseFields())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String onboardingToken = read(login, "data", "accessToken");

    // 온보딩 완료 → 정식 토큰
    String onboarding =
        mockMvc
            .perform(
                post("/api/v1/auth/onboarding")
                    .header(HttpHeaders.AUTHORIZATION, bearer(onboardingToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(onboardingJson()))
            .andExpect(status().isOk())
            .andDo(
                document(
                    "auth-onboarding",
                    resourceDetails().summary("온보딩 제출 — 필수정보·약관 동의로 PENDING→ACTIVE 전이, 정식 토큰 발급"),
                    requestFields(onboardingRequestFields()),
                    responseFields(onboardingResponseFields())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String accessToken = read(onboarding, "data", "accessToken");
    String refreshToken = read(onboarding, "data", "refreshToken");

    // 내 프로필 조회
    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
        .andExpect(status().isOk())
        .andDo(
            document(
                "user-get-me",
                resourceDetails().summary("내 프로필 조회"),
                responseFields(profileResponseFields())));

    // 내 프로필 부분 수정
    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"marketingAgreed\":true}"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "user-patch-me",
                resourceDetails().summary("내 프로필 부분 수정 — 전송한 필드만 변경"),
                requestFields(patchRequestFields()),
                responseFields(profileResponseFields())));

    // 재발급(회전)
    String reissue =
        mockMvc
            .perform(
                post("/api/v1/auth/reissue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isOk())
            .andDo(
                document(
                    "auth-reissue",
                    resourceDetails().summary("토큰 재발급 — refresh로 access 재발급(항상 회전)"),
                    requestFields(
                        refreshTokenRequestField("서버가 발급·보관(해시) 중인 불투명 refresh 토큰(빈값 불가)")),
                    responseFields(
                        tokenResponseFields(
                            "새 access 토큰(JWT)", "새 refresh 토큰(회전 — 제출 토큰은 ROTATED 폐기)"))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String newRefreshToken = read(reissue, "data", "refreshToken");

    // 로그아웃
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + newRefreshToken + "\"}"))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "auth-logout",
                resourceDetails().summary("로그아웃 — 제출 refresh 토큰 무효화(멱등, 204)"),
                requestFields(refreshTokenRequestField("무효화할 refresh 토큰(빈값 불가)"))));

    // 탈퇴
    mockMvc
        .perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
        .andExpect(status().isNoContent())
        .andDo(document("user-withdraw"));
  }

  /**
   * 스펙(01-auth-onboarding.md)의 "발생 가능한 에러" 표를 엔드포인트별로 실제 트리거해 스니펫으로 생성한다. 각 케이스는 status와
   * error.code를 단정해 코드 매핑이 스펙과 일치함을 검증한다.
   */
  @Test
  void generatesAuthOnboardingErrorSnippets() throws Exception {
    // ===== 준비: PENDING/ACTIVE/탈퇴용 사용자 + 위조·만료·미존재 토큰 =====
    String pendingToken =
        read(socialLogin("err-pending"), "data", "accessToken"); // 온보딩 미완료(PENDING) 토큰

    String activeAccess = onboardCompletely("err-active"); // 정식 ACTIVE access 토큰
    String withdrawAccess = onboardCompletely("err-withdraw"); // 탈퇴 시나리오 전용 ACTIVE 토큰

    String expiredToken = expiredAccessToken(); // 서명 유효·만료된 토큰
    String ghostToken = jwtTokenService.issueAccessToken(999_999_999L); // 서명 유효·미존재 userId

    // ===== 1. POST /api/v1/auth/social-login =====
    perform(
        post("/api/v1/auth/social-login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"provider\":\"GOOGLE\",\"idToken\":\"\"}"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-social-login-invalid-input",
        "소셜 로그인 — 입력 검증 실패 (400 INVALID_INPUT): idToken 누락/빈값");

    perform(
        post("/api/v1/auth/social-login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-social-login-malformed",
        "소셜 로그인 — 본문 해석 불가 (400 MALFORMED_REQUEST): JSON 파싱 불가/타입 불일치");

    perform(
        post("/api/v1/auth/social-login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"provider\":\"GOOGLE\",\"idToken\":\"" + INVALID_SOCIAL_TOKEN + "\"}"),
        status().isUnauthorized(),
        "AUTH_INVALID_SOCIAL_TOKEN",
        "auth-social-login-invalid-token",
        "소셜 로그인 — 소셜 토큰 검증 실패 (401 AUTH_INVALID_SOCIAL_TOKEN): 서명/aud/iss/exp 불일치");

    // ===== 2. POST /api/v1/auth/onboarding =====
    perform(
        post("/api/v1/auth/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(onboardingBlankFirstName()),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-onboarding-invalid-input",
        "온보딩 — 입력 검증 실패 (400 INVALID_INPUT): 필수 필드 누락/형식 위반");

    perform(
        post("/api/v1/auth/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-onboarding-malformed",
        "온보딩 — 본문 해석 불가 (400 MALFORMED_REQUEST)");

    perform(
        post("/api/v1/auth/onboarding")
            .contentType(MediaType.APPLICATION_JSON)
            .content(onboardingJson()),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "auth-onboarding-unauthenticated",
        "온보딩 — 인증 누락/위조 (401 UNAUTHENTICATED)");

    perform(
        post("/api/v1/auth/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(onboardingJson()),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "auth-onboarding-token-expired",
        "온보딩 — 액세스 토큰 만료 (401 TOKEN_EXPIRED)");

    perform(
        post("/api/v1/auth/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content(onboardingJson()),
        status().isConflict(),
        "AUTH_ONBOARDING_ALREADY_COMPLETED",
        "auth-onboarding-already-completed",
        "온보딩 — 이미 완료한 사용자의 재요청 (409 AUTH_ONBOARDING_ALREADY_COMPLETED)");

    perform(
        post("/api/v1/auth/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(onboardingNoAgreement()),
        status().isUnprocessableEntity(),
        "AUTH_REQUIRED_AGREEMENT_MISSING",
        "auth-onboarding-agreement-missing",
        "온보딩 — 필수 약관 미동의 (422 AUTH_REQUIRED_AGREEMENT_MISSING)");

    // ===== 3. POST /api/v1/auth/reissue =====
    perform(
        post("/api/v1/auth/reissue")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"\"}"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-reissue-invalid-input",
        "재발급 — 입력 검증 실패 (400 INVALID_INPUT): refreshToken 누락/빈값");

    perform(
        post("/api/v1/auth/reissue")
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-reissue-malformed",
        "재발급 — 본문 해석 불가 (400 MALFORMED_REQUEST)");

    perform(
        post("/api/v1/auth/reissue")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"rt_does_not_exist\"}"),
        status().isUnauthorized(),
        "AUTH_INVALID_REFRESH_TOKEN",
        "auth-reissue-invalid-token",
        "재발급 — refresh 토큰 만료/위조/무효화/재사용 탐지 (401 AUTH_INVALID_REFRESH_TOKEN)");

    // ===== 4. POST /api/v1/auth/logout =====
    perform(
        post("/api/v1/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"\"}"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-logout-invalid-input",
        "로그아웃 — 입력 검증 실패 (400 INVALID_INPUT): refreshToken 누락/빈값");

    perform(
        post("/api/v1/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-logout-malformed",
        "로그아웃 — 본문 해석 불가 (400 MALFORMED_REQUEST)");

    perform(
        post("/api/v1/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"rt_any\"}"),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "auth-logout-unauthenticated",
        "로그아웃 — 인증 누락/위조 (401 UNAUTHENTICATED)");

    perform(
        post("/api/v1/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"rt_any\"}"),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "auth-logout-token-expired",
        "로그아웃 — 액세스 토큰 만료 (401 TOKEN_EXPIRED)");

    perform(
        post("/api/v1/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"rt_any\"}"),
        status().isForbidden(),
        "AUTH_ONBOARDING_REQUIRED",
        "auth-logout-onboarding-required",
        "로그아웃 — 온보딩 미완료(PENDING) 토큰 접근 (403 AUTH_ONBOARDING_REQUIRED)");

    // ===== 5. GET /api/v1/users/me =====
    perform(
        get("/api/v1/users/me"),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "user-get-me-unauthenticated",
        "내 프로필 조회 — 인증 누락/위조 (401 UNAUTHENTICATED)");

    perform(
        get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "user-get-me-token-expired",
        "내 프로필 조회 — 액세스 토큰 만료 (401 TOKEN_EXPIRED)");

    perform(
        get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(pendingToken)),
        status().isForbidden(),
        "AUTH_ONBOARDING_REQUIRED",
        "user-get-me-onboarding-required",
        "내 프로필 조회 — 온보딩 미완료(PENDING) 토큰 접근 (403 AUTH_ONBOARDING_REQUIRED)");

    // ===== 6. PATCH /api/v1/users/me =====
    perform(
        patch("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"birthDate\":\"2999-01-01\"}"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "user-patch-me-invalid-input",
        "내 프로필 수정 — 입력 검증 실패 (400 INVALID_INPUT): birthDate 미래 등");

    perform(
        patch("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"gender\":\"UNKNOWN\"}"),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "user-patch-me-malformed",
        "내 프로필 수정 — 본문 해석 불가 (400 MALFORMED_REQUEST): enum 매핑 실패 등");

    perform(
        patch("/api/v1/users/me")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"marketingAgreed\":true}"),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "user-patch-me-unauthenticated",
        "내 프로필 수정 — 인증 누락/위조 (401 UNAUTHENTICATED)");

    perform(
        patch("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"marketingAgreed\":true}"),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "user-patch-me-token-expired",
        "내 프로필 수정 — 액세스 토큰 만료 (401 TOKEN_EXPIRED)");

    perform(
        patch("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"marketingAgreed\":true}"),
        status().isForbidden(),
        "AUTH_ONBOARDING_REQUIRED",
        "user-patch-me-onboarding-required",
        "내 프로필 수정 — 온보딩 미완료(PENDING) 토큰 접근 (403 AUTH_ONBOARDING_REQUIRED)");

    // ===== 7. DELETE /api/v1/users/me (인증 누락/만료) =====
    perform(
        delete("/api/v1/users/me"),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "user-withdraw-unauthenticated",
        "회원 탈퇴 — 인증 누락/위조 (401 UNAUTHENTICATED)");

    perform(
        delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "user-withdraw-token-expired",
        "회원 탈퇴 — 액세스 토큰 만료 (401 TOKEN_EXPIRED)");

    // 미존재 userId 토큰 → 탈퇴 404
    perform(
        delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(ghostToken)),
        status().isNotFound(),
        "USER_NOT_FOUND",
        "user-withdraw-not-found",
        "회원 탈퇴 — 대상 사용자 없음 (404 USER_NOT_FOUND)");

    // ===== 탈퇴 후 상태 기반 에러(404/409) — withdrawAccess 사용자를 실제 탈퇴시킨 뒤 검증 =====
    mockMvc
        .perform(
            delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(withdrawAccess)))
        .andExpect(status().isNoContent()); // 성공 탈퇴(문서는 happy-path에서 생성)

    perform(
        get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(withdrawAccess)),
        status().isNotFound(),
        "USER_NOT_FOUND",
        "user-get-me-not-found",
        "내 프로필 조회 — 탈퇴/삭제로 사용자 없음 (404 USER_NOT_FOUND)");

    perform(
        patch("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(withdrawAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"marketingAgreed\":true}"),
        status().isNotFound(),
        "USER_NOT_FOUND",
        "user-patch-me-not-found",
        "내 프로필 수정 — 탈퇴/삭제로 사용자 없음 (404 USER_NOT_FOUND)");

    perform(
        delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(withdrawAccess)),
        status().isConflict(),
        "USER_ALREADY_WITHDRAWN",
        "user-withdraw-already-withdrawn",
        "회원 탈퇴 — 이미 탈퇴한 사용자의 재요청 (409 USER_ALREADY_WITHDRAWN)");
  }

  // ---- helpers ----

  /** 에러 케이스 한 건: status·error.code를 단정하고 공통 에러 래퍼 responseFields로 문서화한다. */
  private void perform(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
      org.springframework.test.web.servlet.ResultMatcher expectedStatus,
      String expectedCode,
      String identifier,
      String summary)
      throws Exception {
    mockMvc
        .perform(request)
        .andExpect(expectedStatus)
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(expectedCode))
        .andDo(errorSnippet(identifier, summary));
  }

  private static RestDocumentationResultHandler errorSnippet(String identifier, String summary) {
    return document(
        identifier,
        resource(
            ResourceSnippetParameters.builder()
                .summary(summary)
                .description(
                    "실패 응답 — 공통 래퍼(success=false·data=null·error). 클라이언트는 error.code로 분기한다"
                        + "(error-response-guide §1·§4).")
                .responseFields(errorFields())
                .build()));
  }

  /** 공통 에러 래퍼 필드 기술자. 검증 실패(INVALID_INPUT)·그 외 에러 모두를 함께 기술한다. */
  private static List<FieldDescriptor> errorFields() {
    return List.of(
        fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부 — 에러 응답은 항상 false"),
        fieldWithPath("data")
            .type(JsonFieldType.NULL)
            .optional()
            .description("에러 응답의 data는 항상 null"),
        fieldWithPath("error.code")
            .type(JsonFieldType.STRING)
            .description("에러 식별 코드(UPPER_SNAKE_CASE) — 클라이언트 분기 기준"),
        fieldWithPath("error.message")
            .type(JsonFieldType.STRING)
            .description("사람이 읽는 설명(민감정보 미포함, message로 분기 금지)"),
        fieldWithPath("error.errors")
            .type(JsonFieldType.ARRAY)
            .description("입력 검증 실패 시 필드별 상세 목록. 그 외 에러는 빈 배열"),
        fieldWithPath("error.errors[].field")
            .type(JsonFieldType.STRING)
            .optional()
            .description("검증에 실패한 요청 필드 경로(INVALID_INPUT에서만)"),
        fieldWithPath("error.errors[].reason")
            .type(JsonFieldType.STRING)
            .optional()
            .description("해당 필드의 실패 사유(INVALID_INPUT에서만)"));
  }

  // ---- 성공 응답/요청 필드 기술자 (happy-path requestFields/responseFields) ----

  private static FieldDescriptor field(String path, JsonFieldType type, String description) {
    return fieldWithPath(path).type(type).description(description);
  }

  private static FieldDescriptor optField(String path, JsonFieldType type, String description) {
    return fieldWithPath(path).type(type).optional().description(description);
  }

  private static FieldDescriptor errorNull() {
    return fieldWithPath("error")
        .type(JsonFieldType.NULL)
        .optional()
        .description("성공 응답의 error는 항상 null");
  }

  private static List<FieldDescriptor> socialLoginRequestFields() {
    return List.of(
        field("provider", JsonFieldType.STRING, "소셜 제공자: APPLE | GOOGLE"),
        field("idToken", JsonFieldType.STRING, "provider 발급 OIDC ID 토큰(빈 문자열 불가)"));
  }

  private static List<FieldDescriptor> socialLoginResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field(
            "data.onboardingRequired",
            JsonFieldType.BOOLEAN,
            "온보딩 필요 여부 — 신규/온보딩 미완료=true, 기존 ACTIVE=false"),
        field("data.tokenType", JsonFieldType.STRING, "토큰 타입(Bearer)"),
        field("data.accessToken", JsonFieldType.STRING, "access 토큰(JWT). 신규는 온보딩 전용 스코프"),
        optField(
            "data.refreshToken", JsonFieldType.STRING, "refresh 토큰(불투명). 신규/온보딩 미완료 응답에서는 null"),
        field("data.expiresIn", JsonFieldType.NUMBER, "access 토큰 만료까지 초(온보딩 1800 / 정식 3600)"),
        errorNull());
  }

  private static List<FieldDescriptor> onboardingRequestFields() {
    return List.of(
        field("firstName", JsonFieldType.STRING, "이름(필수)"),
        field("lastName", JsonFieldType.STRING, "성(필수)"),
        field("gender", JsonFieldType.STRING, "성별: MALE | FEMALE(필수)"),
        field("birthDate", JsonFieldType.STRING, "생년월일 YYYY-MM-DD, 과거만(필수)"),
        field("countryCode", JsonFieldType.STRING, "국가번호 예: +82(필수)"),
        field("phoneNumber", JsonFieldType.STRING, "전화번호, 국가번호 제외 숫자(필수)"),
        field(
            "visaType",
            JsonFieldType.STRING,
            "비자유형 enum(필수): VISA_STUDENT|VISA_WORK|VISA_RESIDENCE|VISA_WORKING_HOLIDAY|VISA_TOURISM|VISA_ETC"),
        field("termsOfServiceAgreed", JsonFieldType.BOOLEAN, "이용약관 동의(필수). false면 422"),
        field("privacyPolicyAgreed", JsonFieldType.BOOLEAN, "개인정보처리방침 동의(필수). false면 422"),
        optField("marketingAgreed", JsonFieldType.BOOLEAN, "마케팅 수신 동의(선택, 기본 false)"));
  }

  private static List<FieldDescriptor> refreshTokenRequestField(String description) {
    return List.of(field("refreshToken", JsonFieldType.STRING, description));
  }

  private static List<FieldDescriptor> patchRequestFields() {
    return List.of(
        optField("firstName", JsonFieldType.STRING, "이름(선택)"),
        optField("lastName", JsonFieldType.STRING, "성(선택)"),
        optField("gender", JsonFieldType.STRING, "성별 MALE|FEMALE(선택)"),
        optField("birthDate", JsonFieldType.STRING, "생년월일 YYYY-MM-DD, 과거만(선택)"),
        optField("countryCode", JsonFieldType.STRING, "국가번호(선택)"),
        optField("phoneNumber", JsonFieldType.STRING, "전화번호(선택)"),
        optField("visaType", JsonFieldType.STRING, "비자유형 enum(선택)"),
        optField("marketingAgreed", JsonFieldType.BOOLEAN, "마케팅 수신 동의(선택)"));
  }

  private static List<FieldDescriptor> tokenResponseFields(String accessDesc, String refreshDesc) {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.tokenType", JsonFieldType.STRING, "토큰 타입(Bearer)"),
        field("data.accessToken", JsonFieldType.STRING, accessDesc),
        field("data.refreshToken", JsonFieldType.STRING, refreshDesc),
        field("data.expiresIn", JsonFieldType.NUMBER, "access 토큰 만료까지 초(3600)"),
        errorNull());
  }

  private static List<FieldDescriptor> profileResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.id", JsonFieldType.NUMBER, "회원 ID"),
        field("data.firstName", JsonFieldType.STRING, "이름"),
        field("data.lastName", JsonFieldType.STRING, "성"),
        field("data.gender", JsonFieldType.STRING, "성별(MALE|FEMALE)"),
        field("data.birthDate", JsonFieldType.STRING, "생년월일(YYYY-MM-DD)"),
        field("data.countryCode", JsonFieldType.STRING, "국가번호(예: +82)"),
        field("data.phoneNumber", JsonFieldType.STRING, "전화번호(국가번호 제외)"),
        field("data.visaType", JsonFieldType.STRING, "비자유형 enum"),
        field("data.status", JsonFieldType.STRING, "회원 상태(PENDING|ACTIVE|WITHDRAWN)"),
        field("data.termsOfServiceAgreed", JsonFieldType.BOOLEAN, "이용약관 동의 여부"),
        field("data.privacyPolicyAgreed", JsonFieldType.BOOLEAN, "개인정보처리방침 동의 여부"),
        field("data.marketingAgreed", JsonFieldType.BOOLEAN, "마케팅 수신 동의 여부"),
        field("data.createdAt", JsonFieldType.STRING, "가입 시각(ISO-8601 UTC)"),
        errorNull());
  }

  private static List<FieldDescriptor> onboardingResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.user.id", JsonFieldType.NUMBER, "회원 ID"),
        field("data.user.firstName", JsonFieldType.STRING, "이름"),
        field("data.user.lastName", JsonFieldType.STRING, "성"),
        field("data.user.gender", JsonFieldType.STRING, "성별(MALE|FEMALE)"),
        field("data.user.birthDate", JsonFieldType.STRING, "생년월일(YYYY-MM-DD)"),
        field("data.user.countryCode", JsonFieldType.STRING, "국가번호(예: +82)"),
        field("data.user.phoneNumber", JsonFieldType.STRING, "전화번호(국가번호 제외)"),
        field("data.user.visaType", JsonFieldType.STRING, "비자유형 enum"),
        field("data.user.status", JsonFieldType.STRING, "회원 상태(ACTIVE)"),
        field("data.user.marketingAgreed", JsonFieldType.BOOLEAN, "마케팅 수신 동의 여부"),
        field("data.user.createdAt", JsonFieldType.STRING, "가입 시각(ISO-8601 UTC)"),
        field("data.tokenType", JsonFieldType.STRING, "토큰 타입(Bearer)"),
        field(
            "data.accessToken",
            JsonFieldType.STRING,
            "정식 access 토큰(JWT, onboardingCompleted=true)"),
        field("data.refreshToken", JsonFieldType.STRING, "정식 refresh 토큰(불투명)"),
        field("data.expiresIn", JsonFieldType.NUMBER, "access 토큰 만료까지 초(3600)"),
        errorNull());
  }

  /** 신규 소셜 로그인으로 PENDING 사용자를 만들고 응답 본문을 돌려준다. */
  private String socialLogin(String subject) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/social-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"GOOGLE\",\"idToken\":\"" + subject + "\"}"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** 신규 소셜 로그인 → 온보딩 완료까지 수행하고 정식 access 토큰을 돌려준다. */
  private String onboardCompletely(String subject) throws Exception {
    String onboardingToken = read(socialLogin(subject), "data", "accessToken");
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/onboarding")
                    .header(HttpHeaders.AUTHORIZATION, bearer(onboardingToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(onboardingJson()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return read(body, "data", "accessToken");
  }

  /** 서명은 유효하나 만료된 access 토큰(JwtTokenService와 동일 시크릿·issuer·클레임). */
  private String expiredAccessToken() {
    SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(jwtProperties.getIssuer())
        .subject("1")
        .claim("onboardingCompleted", true)
        .issuedAt(Date.from(now.minusSeconds(7200)))
        .expiration(Date.from(now.minusSeconds(3600)))
        .signWith(key)
        .compact();
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

  private static String onboardingBlankFirstName() {
    return """
        {
          "firstName": "",
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

  private static String onboardingNoAgreement() {
    return """
        {
          "firstName": "Gil",
          "lastName": "Hong",
          "gender": "MALE",
          "birthDate": "1990-01-01",
          "countryCode": "+82",
          "phoneNumber": "1012345678",
          "visaType": "VISA_WORK",
          "termsOfServiceAgreed": false,
          "privacyPolicyAgreed": true,
          "marketingAgreed": false
        }
        """;
  }
}
