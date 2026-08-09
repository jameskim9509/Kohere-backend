package com.kohere.auth;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails;
import static com.kohere.docs.ApiDocsErrors.assertError;
import static com.kohere.docs.ApiDocsErrors.errorSnippet;
import static com.kohere.docs.ApiDocsFields.codeField;
import static com.kohere.docs.ApiDocsFields.enumField;
import static com.kohere.docs.ApiDocsFields.errorNull;
import static com.kohere.docs.ApiDocsFields.field;
import static com.kohere.docs.ApiDocsFields.optCodeField;
import static com.kohere.docs.ApiDocsFields.optEnumField;
import static com.kohere.docs.ApiDocsFields.optField;
import static com.kohere.docs.DocsTokens.bearer;
import static com.kohere.docs.DocsTokens.expiredAccessToken;
import static com.kohere.docs.UserProfileDocsFields.COUNTRY_CODES;
import static com.kohere.docs.UserProfileDocsFields.LANG_CODES;
import static com.kohere.docs.UserProfileDocsFields.ME_401;
import static com.kohere.docs.UserProfileDocsFields.ME_403;
import static com.kohere.docs.UserProfileDocsFields.ME_404;
import static com.kohere.docs.UserProfileDocsFields.ME_DESCRIPTION;
import static com.kohere.docs.UserProfileDocsFields.ME_SUMMARY;
import static com.kohere.docs.UserProfileDocsFields.PATCH_ME_400;
import static com.kohere.docs.UserProfileDocsFields.PATCH_ME_401;
import static com.kohere.docs.UserProfileDocsFields.PATCH_ME_403;
import static com.kohere.docs.UserProfileDocsFields.PATCH_ME_404;
import static com.kohere.docs.UserProfileDocsFields.PATCH_ME_DESCRIPTION;
import static com.kohere.docs.UserProfileDocsFields.PATCH_ME_SUMMARY;
import static com.kohere.docs.UserProfileDocsFields.TOKEN_TYPES;
import static com.kohere.docs.UserProfileDocsFields.meResponseFields;
import static com.kohere.docs.UserProfileDocsFields.onboardingResponseFields;
import static com.kohere.docs.UserProfileDocsFields.patchRequestFields;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kohere.TestcontainersConfiguration;
import com.kohere.auth.application.EmailVerificationProperties;
import com.kohere.auth.domain.AppleAuthClient;
import com.kohere.auth.domain.EmailDispatchException;
import com.kohere.auth.domain.InvalidSocialTokenException;
import com.kohere.auth.domain.OidcTokenVerifier;
import com.kohere.auth.domain.OidcUser;
import com.kohere.auth.domain.Provider;
import com.kohere.auth.domain.VerificationEmailSender;
import com.kohere.common.security.JwtProperties;
import com.kohere.common.security.JwtTokenService;
import com.kohere.docs.ApiDocsTags;
import com.kohere.user.domain.Gender;
import com.kohere.user.domain.Occupation;
import com.kohere.user.domain.UserStatus;
import com.kohere.user.domain.VisaType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Spring REST Docs 스니펫 생성 테스트(ADR-0007). auth-onboarding 엔드포인트(소셜 로그인·약관 동의·이메일
 * 인증·온보딩·재발급·로그아웃·프로필·탈퇴)의 성공 응답과, 스펙(01-auth-onboarding.md)에 정의된 주요 에러 응답을 {@code
 * build/generated-snippets}에 생성한다(소셜 OIDC만 가짜 주입, 메일 발송은 모킹, Security·JPA·Redis·JWT는 실제 구동).
 *
 * <p>흐름: 소셜로그인(PENDING) → 약관 동의(TERMS_AGREED) → 이메일 인증(코드 발송·확인) → 온보딩(ACTIVE). 메일 발송은 {@link
 * VerificationEmailSender}를 모킹해 인증번호를 캡처한다.
 *
 * <p><b>문서 규약(#151)</b> — 오퍼레이션(path+method)당 summary/description 상수를 <b>1벌</b>만 두고 그 오퍼레이션의 성공·에러
 * 스니펫이 전부 같은 문자열·같은 태그를 쓴다. 케이스 구분은 summary가 아니라 document identifier로 한다(그게 Swagger Examples 드롭다운
 * 항목명이다). {@code GET /users/me}는 {@code LandlordOnboardingDocsTest}와 스니펫을 공유하므로 문구·필드 기술자를 {@code
 * com.kohere.docs.UserProfileDocsFields}에서 가져온다.
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthOnboardingDocsTest {

  private static final String INVALID_SOCIAL_TOKEN = "invalid-social-token";
  private static final String MALFORMED_BODY = "{ \"oops\" }";

  /** 이 접두사로 시작하는 idToken은 {@link FakeOidcConfig}가 email 클레임 없는 소셜 계정으로 취급한다. */
  private static final String NO_EMAIL_PREFIX = "noemail-";

  // ---- 오퍼레이션 문구·에러코드 상수(규약 1·3·4·11) ----

  private static final String SOCIAL_LOGIN_SUMMARY = "소셜 로그인";

  private static final String SOCIAL_LOGIN_DESCRIPTION =
      """
      소셜 자격(Apple/Google)을 검증하고 서버 토큰을 발급한다. 신규면 계정을 만들고 온보딩 전용 토큰만 준다.

      인증: 불필요(공개). 요청 본문의 소셜 자격만으로 판정한다.

      - provider별 자격이 다르다 — `GOOGLE`은 `idToken`, `APPLE`은 1회용 `authorizationCode`(약 5분 만료)다. 누락·빈값은 400 `AUTH_MISSING_CREDENTIAL`이다.
      - 응답 `status`가 클라이언트 재개 지점을 정한다 — `PENDING`은 약관 동의, `TERMS_AGREED`는 온보딩, `ACTIVE`는 홈이다.
      - 온보딩 미완료 응답은 `refreshToken`이 null이고 `accessToken`이 온보딩 전용 스코프(`expiresIn` 1800)다. `ACTIVE`는 정식 access+refresh(3600)를 받는다.
      - `email`·`name`은 최초 로그인에서만 캡처하고 재로그인 요청 값은 무시한다. `email`은 토큰의 email 클레임과 교차 검증한다(#192).

      에러: 400 `INVALID_INPUT`·`AUTH_MISSING_CREDENTIAL`·`MALFORMED_REQUEST`, 401 `AUTH_INVALID_SOCIAL_TOKEN`, 422 `AUTH_EMAIL_MISMATCH`·`AUTH_EMAIL_REQUIRED`.
      """;

  private static final String[] SOCIAL_LOGIN_400 = {
    "INVALID_INPUT", "AUTH_MISSING_CREDENTIAL", "MALFORMED_REQUEST"
  };
  private static final String[] SOCIAL_LOGIN_401 = {"AUTH_INVALID_SOCIAL_TOKEN"};
  private static final String[] SOCIAL_LOGIN_422 = {"AUTH_EMAIL_MISMATCH", "AUTH_EMAIL_REQUIRED"};

  private static final String TERMS_SUMMARY = "약관 동의";

  private static final String TERMS_DESCRIPTION =
      """
      이용약관·개인정보처리방침·마케팅 동의를 기록하고 `PENDING`을 `TERMS_AGREED`로 전이한다.

      인증: 필수(온보딩 토큰 `ROLE_ONBOARDING`). 이미 온보딩을 마친(`ACTIVE`) 사용자의 재요청은 409다.

      - 필수 동의 2종(`termsOfServiceAgreed`·`privacyPolicyAgreed`)은 누락(null)이면 400, `false`면 422다.
      - `marketingAgreed`는 선택이며 미전송이면 false로 기록한다.
      - 응답 `status`는 전이 후 값이라 항상 `TERMS_AGREED`다.

      에러: 400 `INVALID_INPUT`·`MALFORMED_REQUEST`, 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED`, 409 `AUTH_ONBOARDING_ALREADY_COMPLETED`, 422 `AUTH_REQUIRED_AGREEMENT_MISSING`.
      """;

  private static final String[] TERMS_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  private static final String[] TERMS_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  private static final String[] TERMS_409 = {"AUTH_ONBOARDING_ALREADY_COMPLETED"};
  private static final String[] TERMS_422 = {"AUTH_REQUIRED_AGREEMENT_MISSING"};

  private static final String EMAIL_CODE_SUMMARY = "이메일 인증번호 발송";

  private static final String EMAIL_CODE_DESCRIPTION =
      """
      입력한 이메일로 인증번호를 동기 발송하고 챌린지를 저장한다. 응답 `email`은 마스킹된다(예 `mi***@example.com`).

      인증: 필수(정식 토큰 — `ACTIVE`·`ROLE_USER`). 온보딩 토큰으로 호출하면 403이다(#192에서 온보딩 단계 전용 → 정식 전용으로 반전).

      - 발송이 실패하면(메일 provider 장애·타임아웃) 챌린지를 저장하지 않고 502다.
      - 재발송 간격을 채우지 않은 재요청은 429다.
      - `expiresIn`은 인증번호 만료까지의 초다.

      에러: 400 `INVALID_INPUT`·`MALFORMED_REQUEST`, 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED`, 403 `AUTH_ONBOARDING_REQUIRED`, 429 `TOO_MANY_REQUESTS`, 502 `UPSTREAM_ERROR`.
      """;

  private static final String[] EMAIL_CODE_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  private static final String[] EMAIL_CODE_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  private static final String[] EMAIL_CODE_403 = {"AUTH_ONBOARDING_REQUIRED"};
  private static final String[] EMAIL_CODE_429 = {"TOO_MANY_REQUESTS"};
  private static final String[] EMAIL_CODE_502 = {"UPSTREAM_ERROR"};

  private static final String EMAIL_VERIFY_SUMMARY = "이메일 인증번호 확인";

  private static final String EMAIL_VERIFY_DESCRIPTION =
      """
      발송된 인증번호를 확인해 이메일을 검증 완료(VERIFIED)로 마킹한다.

      인증: 필수(정식 토큰 — `ACTIVE`·`ROLE_USER`). 온보딩 토큰으로 호출하면 403이다.

      - `email`은 인증번호를 발송한 이메일과 일치해야 한다.
      - 챌린지 부재·만료·코드 불일치는 모두 422 하나로 응답한다(어느 쪽인지 구분해 주지 않는다).
      - 코드 불일치가 시도 상한까지 누적되면 429로 잠긴다.

      에러: 400 `INVALID_INPUT`·`MALFORMED_REQUEST`, 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED`, 403 `AUTH_ONBOARDING_REQUIRED`, 422 `AUTH_EMAIL_VERIFICATION_FAILED`, 429 `TOO_MANY_REQUESTS`.
      """;

  private static final String[] EMAIL_VERIFY_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  private static final String[] EMAIL_VERIFY_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  private static final String[] EMAIL_VERIFY_403 = {"AUTH_ONBOARDING_REQUIRED"};
  private static final String[] EMAIL_VERIFY_422 = {"AUTH_EMAIL_VERIFICATION_FAILED"};
  private static final String[] EMAIL_VERIFY_429 = {"TOO_MANY_REQUESTS"};

  private static final String ONBOARDING_SUMMARY = "세입자 온보딩 제출";

  private static final String ONBOARDING_DESCRIPTION =
      """
      세입자 필수 프로필을 제출해 `TERMS_AGREED`를 `ACTIVE`로 전이하고, 닉네임 배정·정식 토큰 발급까지 한 번에 끝낸다.

      인증: 필수(온보딩 토큰, 상태 `TERMS_AGREED`). 약관 미동의(`PENDING`)면 422, 이미 완료(`ACTIVE`)면 409다.

      - 필수는 `gender`·`birthDate`(과거 날짜만)·`country`·`visaType`, 선택은 `occupation`·`lang`이다.
      - 이름·이메일은 소셜 로그인 시점에 확정돼 여기서 받지 않는다(#192).
      - enum·날짜를 String으로 받아 서버가 파싱하므로 값 위반도 400 `INVALID_INPUT`이다(요청 DTO가 enum 타입이라 `MALFORMED_REQUEST`인 `PATCH /users/me`와 갈리는 지점).
      - 이메일 인증 선행 게이트는 폐지됐다 — 약관만 동의하면 곧바로 제출할 수 있다(#192).
      - 응답 `data.user`의 `phoneNumber`는 세입자 미수집이라 필드 자체가 생략된다.

      에러: 400 `INVALID_INPUT`·`MALFORMED_REQUEST`, 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED`, 409 `AUTH_ONBOARDING_ALREADY_COMPLETED`, 422 `AUTH_TERMS_AGREEMENT_REQUIRED`.
      """;

  private static final String[] ONBOARDING_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  private static final String[] ONBOARDING_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  private static final String[] ONBOARDING_409 = {"AUTH_ONBOARDING_ALREADY_COMPLETED"};
  private static final String[] ONBOARDING_422 = {"AUTH_TERMS_AGREEMENT_REQUIRED"};

  private static final String REISSUE_SUMMARY = "토큰 재발급";

  private static final String REISSUE_DESCRIPTION =
      """
      본문의 refresh 토큰으로 access 토큰을 재발급한다. refresh 토큰은 항상 회전한다.

      인증: 불필요(공개 티어). 클라이언트가 모든 요청에 access 토큰을 붙이는 구조라 만료된 access 토큰이 헤더에 실려 와도 401로 막지 않는다(재발급 교착 방지, #181).

      - 제출한 refresh 토큰은 `ROTATED`로 폐기되고 새 refresh 토큰이 함께 내려온다 — 응답의 새 토큰으로 교체해야 다음 재발급이 된다.
      - 만료·위조·무효화·재사용 탐지는 모두 401 `AUTH_INVALID_REFRESH_TOKEN` 하나로 응답한다.

      에러: 400 `INVALID_INPUT`·`MALFORMED_REQUEST`, 401 `AUTH_INVALID_REFRESH_TOKEN`.
      """;

  private static final String[] REISSUE_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  private static final String[] REISSUE_401 = {"AUTH_INVALID_REFRESH_TOKEN"};

  private static final String LOGOUT_SUMMARY = "로그아웃";

  private static final String LOGOUT_DESCRIPTION =
      """
      제출한 refresh 토큰을 무효화한다. 이미 무효한 토큰이어도 204다(멱등).

      인증: 필수(정식 토큰 — `ROLE_USER`). 온보딩 미완료(`PENDING`·`TERMS_AGREED`) 토큰 접근은 403이다.

      - 성공 응답에는 본문이 없다(204).
      - access 토큰은 stateless라 서버가 폐기하지 않는다 — 클라이언트가 버리고, 남은 만료 시간까지는 서명상 유효하다.

      에러: 400 `INVALID_INPUT`·`MALFORMED_REQUEST`, 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED`, 403 `AUTH_ONBOARDING_REQUIRED`.
      """;

  private static final String[] LOGOUT_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  private static final String[] LOGOUT_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  private static final String[] LOGOUT_403 = {"AUTH_ONBOARDING_REQUIRED"};

  // PATCH /users/me 문구·에러코드·요청 필드는 임대인 예시(LandlordOnboardingDocsTest의 user-patch-me-landlord)와
  // 같은 오퍼레이션이라 UserProfileDocsFields에서 공유한다(GET /users/me와 같은 이유).

  private static final String WITHDRAW_SUMMARY = "회원 탈퇴";

  private static final String WITHDRAW_DESCRIPTION =
      """
      본인 계정을 `WITHDRAWN`으로 전이하고 PII를 즉시 익명화하며 refresh 토큰을 일괄 무효화한다.

      인증: 필수. 온보딩 미완료(`PENDING`·`TERMS_AGREED`) 사용자도 탈퇴할 수 있다(온보딩 중단 정리 목적이라 403을 두지 않았다).

      - 성공 응답에는 본문이 없다(204).
      - Apple 연동 계정은 매핑 삭제 전에 Apple `/auth/revoke`로 연동까지 폐기한다(best-effort — Apple 장애여도 탈퇴는 완료).
      - 탈퇴 후 같은 토큰으로 프로필을 조회하면 404, 탈퇴를 재요청하면 409다.

      에러: 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED`, 404 `USER_NOT_FOUND`, 409 `USER_ALREADY_WITHDRAWN`.
      """;

  private static final String[] WITHDRAW_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  private static final String[] WITHDRAW_404 = {"USER_NOT_FOUND"};
  private static final String[] WITHDRAW_409 = {"USER_ALREADY_WITHDRAWN"};

  // 서명이 깨진(다른 키로 서명) 액세스 토큰. 서버 검증에서 401 UNAUTHENTICATED 를 유발하면서도 구조상 JWT 라,
  // restdocs-api-spec 이 "무인증" 예시에서도 bearerAuthJWT 보안 스킴을 도출하게 한다. 무인증 예시는 본래 헤더를
  // 안 보내는데, 그 예시가 오퍼레이션 "대표"로 뽑히면(스니펫 병합 순서는 비결정적) security 가 통째로 누락된다 →
  // Swagger 자물쇠 사라지고 토큰 미전송 → 401. 모든 예시가 Bearer JWT 헤더를 갖게 해 순서와 무관하게 막는다.
  private static final String FORGED_TOKEN =
      Jwts.builder()
          .issuer("kohere")
          .subject("1")
          .claim("onboardingCompleted", true)
          .signWith(
              Keys.hmacShaKeyFor(
                  "forged-doc-only-wrong-secret-please-override-32bytes-min!!"
                      .getBytes(StandardCharsets.UTF_8)))
          .compact();

  @TestConfiguration
  static class FakeOidcConfig {
    @Bean
    @Primary
    OidcTokenVerifier fakeOidcTokenVerifier() {
      return (provider, idToken) -> {
        if (INVALID_SOCIAL_TOKEN.equals(idToken)) {
          throw new InvalidSocialTokenException();
        }
        // provider가 email 클레임을 주지 않는 계정(Apple 이메일 가리기 등)을 재현한다 — 요청에도 email이 없으면
        // 422 AUTH_EMAIL_REQUIRED다. 다른 테스트의 idToken은 이 접두사를 쓰지 않는다.
        String email = idToken.startsWith(NO_EMAIL_PREFIX) ? null : idToken + "@example.com";
        return new OidcUser(provider, idToken, email);
      };
    }
  }

  @Autowired private WebApplicationContext context;
  @Autowired private JwtTokenService jwtTokenService;
  @Autowired private JwtProperties jwtProperties;
  @Autowired private EmailVerificationProperties emailProperties;
  @MockitoBean private VerificationEmailSender emailSender;
  @MockitoBean private AppleAuthClient appleAuthClient; // 실제 Apple HTTP 대체
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Map<String, String> sentCodes = new ConcurrentHashMap<>();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp(RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .apply(documentationConfiguration(restDocumentation))
            .build();
    // 발송 인증번호를 이메일별로 기록(검증 단계에서 사용). 실제 메일 발송은 하지 않는다.
    doAnswer(
            inv -> {
              sentCodes.put(inv.getArgument(0), inv.getArgument(1));
              return null;
            })
        .when(emailSender)
        .send(any(), any());
  }

  @Test
  void generatesAuthOnboardingSnippets() throws Exception {
    String email = emailFor("docs-sub-1");
    when(appleAuthClient.exchangeAuthorizationCode("docs-apple-code"))
        .thenReturn(new AppleAuthClient.AppleTokens("docs-apple-sub", "docs-apple-rt"));

    // 소셜 로그인(신규) → 온보딩 임시 토큰. email·name은 앱이 함께 보내 최초 로그인에서 캡처된다(#192).
    // email은 토큰 email 클레임(docs-sub-1@example.com)과 교차 검증되므로 동일 값을 보낸다.
    String login =
        mockMvc
            .perform(
                post("/api/v1/auth/social-login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"provider\":\"GOOGLE\",\"idToken\":\"docs-sub-1\",\"email\":\"docs-sub-1@example.com\",\"name\":\"Minh Nguyen\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Minh Nguyen"))
            .andExpect(jsonPath("$.data.email").value("docs-sub-1@example.com"))
            .andDo(
                document(
                    "auth-social-login",
                    resourceDetails()
                        .tag(ApiDocsTags.AUTH)
                        .summary(SOCIAL_LOGIN_SUMMARY)
                        .description(SOCIAL_LOGIN_DESCRIPTION),
                    requestFields(socialLoginRequestFields()),
                    responseFields(socialLoginResponseFields())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String onboardingToken = read(login, "data", "accessToken");

    // 소셜 로그인(Apple, 신규) — authorizationCode 교환 후 동일 응답 형태(요청 바디만 provider별 분기)
    mockMvc
        .perform(
            post("/api/v1/auth/social-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"APPLE\",\"authorizationCode\":\"docs-apple-code\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andDo(
            document(
                "auth-social-login-apple",
                resourceDetails()
                    .tag(ApiDocsTags.AUTH)
                    .summary(SOCIAL_LOGIN_SUMMARY)
                    .description(SOCIAL_LOGIN_DESCRIPTION),
                requestFields(socialLoginRequestFields()),
                responseFields(socialLoginResponseFields())));

    // 약관 동의 → TERMS_AGREED
    mockMvc
        .perform(
            post("/api/v1/auth/terms")
                .header(HttpHeaders.AUTHORIZATION, bearer(onboardingToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(termsJson(true, true, false)))
        .andExpect(status().isOk())
        .andDo(
            document(
                "auth-terms",
                resourceDetails()
                    .tag(ApiDocsTags.AUTH)
                    .summary(TERMS_SUMMARY)
                    .description(TERMS_DESCRIPTION),
                requestFields(termsRequestFields()),
                responseFields(termsResponseFields())));

    // 온보딩 완료 → 정식 토큰(이메일 인증은 온보딩과 분리돼 정식 토큰 전용 — 아래에서 별도 생성, #192)
    // 규약 13: 응답 필드를 세입자·임대인 합집합으로 합치며 phoneNumber를 optional로 낮췄으므로, 세입자 응답에
    // 그 필드가 없다는 계약을 단정으로 되메운다.
    String onboarding =
        mockMvc
            .perform(
                post("/api/v1/auth/onboarding")
                    .header(HttpHeaders.AUTHORIZATION, bearer(onboardingToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(onboardingJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.phoneNumber").doesNotExist())
            .andDo(
                document(
                    "auth-onboarding",
                    resourceDetails()
                        .tag(ApiDocsTags.AUTH)
                        .summary(ONBOARDING_SUMMARY)
                        .description(ONBOARDING_DESCRIPTION),
                    requestFields(onboardingRequestFields()),
                    responseFields(onboardingResponseFields())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String accessToken = read(onboarding, "data", "accessToken");
    String refreshToken = read(onboarding, "data", "refreshToken");

    // 기존 회원(ACTIVE) 재로그인 — 정식 access+refresh 발급(status=ACTIVE, onboardingRequired=false)
    mockMvc
        .perform(
            post("/api/v1/auth/social-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"GOOGLE\",\"idToken\":\"docs-sub-1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.onboardingRequired").value(false))
        .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
        .andDo(
            document(
                "auth-social-login-active",
                resourceDetails()
                    .tag(ApiDocsTags.AUTH)
                    .summary(SOCIAL_LOGIN_SUMMARY)
                    .description(SOCIAL_LOGIN_DESCRIPTION),
                requestFields(socialLoginRequestFields()),
                responseFields(socialLoginResponseFields())));

    // 이메일 인증번호 발송 — 정식(ACTIVE) 토큰 전용(#192에서 온보딩 단계 전용→정식 전용으로 반전)
    mockMvc
        .perform(
            post("/api/v1/auth/email/verification-code")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "auth-email-verification-code",
                resourceDetails()
                    .tag(ApiDocsTags.AUTH)
                    .summary(EMAIL_CODE_SUMMARY)
                    .description(EMAIL_CODE_DESCRIPTION),
                requestFields(emailCodeRequestFields()),
                responseFields(emailCodeResponseFields())));

    // 이메일 인증번호 확인 — 정식(ACTIVE) 토큰 전용
    mockMvc
        .perform(
            post("/api/v1/auth/email/verify")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + sentCodes.get(email) + "\"}"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "auth-email-verify",
                resourceDetails()
                    .tag(ApiDocsTags.AUTH)
                    .summary(EMAIL_VERIFY_SUMMARY)
                    .description(EMAIL_VERIFY_DESCRIPTION),
                requestFields(emailVerifyRequestFields()),
                responseFields(emailVerifyResponseFields())));

    // 내 프로필 조회(세입자) — 규약 13: phoneNumber는 임대인 전용이라 세입자 응답에 없다.
    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lang").value("ko"))
        .andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
        .andDo(
            document(
                "user-get-me",
                resourceDetails()
                    .tag(ApiDocsTags.USERS)
                    .summary(ME_SUMMARY)
                    .description(ME_DESCRIPTION),
                responseFields(meResponseFields())));

    // 내 프로필 부분 수정
    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lang\":\"en\",\"marketingAgreed\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lang").value("en"))
        .andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
        .andDo(
            document(
                "user-patch-me",
                resourceDetails()
                    .tag(ApiDocsTags.USERS)
                    .summary(PATCH_ME_SUMMARY)
                    .description(PATCH_ME_DESCRIPTION),
                requestFields(patchRequestFields()),
                responseFields(meResponseFields())));

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
                    resourceDetails()
                        .tag(ApiDocsTags.AUTH)
                        .summary(REISSUE_SUMMARY)
                        .description(REISSUE_DESCRIPTION),
                    requestFields(
                        refreshTokenRequestField("서버가 발급·보관(해시) 중인 불투명 refresh 토큰(빈값 불가)")),
                    responseFields(reissueResponseFields())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String newRefreshToken = read(reissue, "data", "refreshToken");

    // 재발급 교착 방지(#181) — 클라이언트가 모든 요청에 access 토큰을 붙이는 구조라 재발급 요청에도 만료된 access
    // 토큰이 실려 온다. reissue는 공개 티어(PublicPaths)라 이때 만료 토큰을 401로 막지 않고 통과시켜야 재발급이
    // 가능하다. 스니펫은 만들지 않는다(auth-reissue가 계약 문서). 회전된 refresh 토큰을 로그아웃으로 잇는다.
    String staleAccessReissue =
        mockMvc
            .perform(
                post("/api/v1/auth/reissue")
                    .header(HttpHeaders.AUTHORIZATION, bearer(expiredAccessToken(jwtProperties)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"" + newRefreshToken + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    newRefreshToken = read(staleAccessReissue, "data", "refreshToken");

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
                resourceDetails()
                    .tag(ApiDocsTags.AUTH)
                    .summary(LOGOUT_SUMMARY)
                    .description(LOGOUT_DESCRIPTION),
                requestFields(refreshTokenRequestField("무효화할 refresh 토큰(빈값 불가)"))));

    // 탈퇴
    mockMvc
        .perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "user-withdraw",
                resourceDetails()
                    .tag(ApiDocsTags.USERS)
                    .summary(WITHDRAW_SUMMARY)
                    .description(WITHDRAW_DESCRIPTION)));
  }

  /** 스펙의 "발생 가능한 에러"를 엔드포인트별로 실제 트리거해 스니펫으로 생성하고 status·error.code를 단정한다. */
  @Test
  void generatesAuthOnboardingErrorSnippets() throws Exception {
    String pendingToken = read(socialLogin("err-pending"), "data", "accessToken"); // PENDING 토큰
    String activeAccess = onboardCompletely("err-active"); // 정식 ACTIVE access 토큰
    String withdrawAccess = onboardCompletely("err-withdraw"); // 탈퇴 시나리오 전용 ACTIVE 토큰

    String expiredToken = expiredAccessToken(jwtProperties);
    String ghostToken = jwtTokenService.issueAccessToken(999_999_999L);

    // ===== social-login =====
    perform(
        post("/api/v1/auth/social-login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"idToken\":\"docs-sub-1\"}"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-social-login-invalid-input",
        ApiDocsTags.AUTH,
        SOCIAL_LOGIN_SUMMARY,
        SOCIAL_LOGIN_DESCRIPTION,
        SOCIAL_LOGIN_400);

    perform(
        post("/api/v1/auth/social-login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"provider\":\"GOOGLE\",\"idToken\":\"\"}"),
        status().isBadRequest(),
        "AUTH_MISSING_CREDENTIAL",
        "auth-social-login-missing-credential",
        ApiDocsTags.AUTH,
        SOCIAL_LOGIN_SUMMARY,
        SOCIAL_LOGIN_DESCRIPTION,
        SOCIAL_LOGIN_400);

    // 문서 스니펫은 본문 없이 만든다(#151-4) — 본문 누락도 같은 MALFORMED_REQUEST 라 예시가 중복되지 않는다.
    // 「깨진 JSON 거부」계약은 스니펫 없이 단정만 남겨 회귀를 막는다.
    assertError(
        mockMvc,
        post("/api/v1/auth/social-login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST");
    perform(
        post("/api/v1/auth/social-login").contentType(MediaType.APPLICATION_JSON),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-social-login-malformed",
        ApiDocsTags.AUTH,
        SOCIAL_LOGIN_SUMMARY,
        SOCIAL_LOGIN_DESCRIPTION,
        SOCIAL_LOGIN_400);

    perform(
        post("/api/v1/auth/social-login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"provider\":\"GOOGLE\",\"idToken\":\"" + INVALID_SOCIAL_TOKEN + "\"}"),
        status().isUnauthorized(),
        "AUTH_INVALID_SOCIAL_TOKEN",
        "auth-social-login-invalid-token",
        ApiDocsTags.AUTH,
        SOCIAL_LOGIN_SUMMARY,
        SOCIAL_LOGIN_DESCRIPTION,
        SOCIAL_LOGIN_401);

    // 최초 로그인(신규 가입)에서 요청 email이 소셜 토큰 email 클레임과 다르면 422 (#192 교차 검증).
    // 서비스 단계 판정이라 유효 본문이 필요하다(#151-4).
    perform(
        post("/api/v1/auth/social-login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"provider\":\"GOOGLE\",\"idToken\":\"err-mismatch\",\"email\":\"typo@example.com\"}"),
        status().isUnprocessableEntity(),
        "AUTH_EMAIL_MISMATCH",
        "auth-social-login-email-mismatch",
        ApiDocsTags.AUTH,
        SOCIAL_LOGIN_SUMMARY,
        SOCIAL_LOGIN_DESCRIPTION,
        SOCIAL_LOGIN_422);

    // 토큰에도 요청에도 email이 없으면 계정을 만들 수 없어 422 (provider가 email 클레임을 주지 않는 계정).
    perform(
        post("/api/v1/auth/social-login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"provider\":\"GOOGLE\",\"idToken\":\"" + NO_EMAIL_PREFIX + "1\"}"),
        status().isUnprocessableEntity(),
        "AUTH_EMAIL_REQUIRED",
        "auth-social-login-email-required",
        ApiDocsTags.AUTH,
        SOCIAL_LOGIN_SUMMARY,
        SOCIAL_LOGIN_DESCRIPTION,
        SOCIAL_LOGIN_422);

    // ===== terms =====
    perform(
        post("/api/v1/auth/terms")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(termsJson(false, true, false)),
        status().isUnprocessableEntity(),
        "AUTH_REQUIRED_AGREEMENT_MISSING",
        "auth-terms-agreement-missing",
        ApiDocsTags.AUTH,
        TERMS_SUMMARY,
        TERMS_DESCRIPTION,
        TERMS_422);

    perform(
        post("/api/v1/auth/terms")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"marketingAgreed\":false}"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-terms-invalid-input",
        ApiDocsTags.AUTH,
        TERMS_SUMMARY,
        TERMS_DESCRIPTION,
        TERMS_400);

    assertError(
        mockMvc,
        post("/api/v1/auth/terms")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST");
    perform(
        post("/api/v1/auth/terms")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-terms-malformed",
        ApiDocsTags.AUTH,
        TERMS_SUMMARY,
        TERMS_DESCRIPTION,
        TERMS_400);

    // 401/403은 시큐리티 필터가 본문 파싱 전에 끊는다 — 요청 본문 없이도 같은 에러다(#151-4).
    perform(
        post("/api/v1/auth/terms")
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "auth-terms-unauthenticated",
        ApiDocsTags.AUTH,
        TERMS_SUMMARY,
        TERMS_DESCRIPTION,
        TERMS_401);

    perform(
        post("/api/v1/auth/terms")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "auth-terms-token-expired",
        ApiDocsTags.AUTH,
        TERMS_SUMMARY,
        TERMS_DESCRIPTION,
        TERMS_401);

    perform(
        post("/api/v1/auth/terms")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content(termsJson(true, true, false)),
        status().isConflict(),
        "AUTH_ONBOARDING_ALREADY_COMPLETED",
        "auth-terms-already-completed",
        ApiDocsTags.AUTH,
        TERMS_SUMMARY,
        TERMS_DESCRIPTION,
        TERMS_409);

    // ===== email verification-code (정식 ACTIVE 토큰 전용 — #192) =====
    perform(
        post("/api/v1/auth/email/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"\"}"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-email-verification-code-invalid-input",
        ApiDocsTags.AUTH,
        EMAIL_CODE_SUMMARY,
        EMAIL_CODE_DESCRIPTION,
        EMAIL_CODE_400);

    assertError(
        mockMvc,
        post("/api/v1/auth/email/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST");
    perform(
        post("/api/v1/auth/email/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-email-verification-code-malformed",
        ApiDocsTags.AUTH,
        EMAIL_CODE_SUMMARY,
        EMAIL_CODE_DESCRIPTION,
        EMAIL_CODE_400);

    perform(
        post("/api/v1/auth/email/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "auth-email-verification-code-unauthenticated",
        ApiDocsTags.AUTH,
        EMAIL_CODE_SUMMARY,
        EMAIL_CODE_DESCRIPTION,
        EMAIL_CODE_401);

    perform(
        post("/api/v1/auth/email/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "auth-email-verification-code-token-expired",
        ApiDocsTags.AUTH,
        EMAIL_CODE_SUMMARY,
        EMAIL_CODE_DESCRIPTION,
        EMAIL_CODE_401);

    // 온보딩 미완료(온보딩 토큰, ROLE_ONBOARDING) 호출 → 정식 토큰 필요 403 (#192 반전).
    // 인가는 필터 체인에서 끝나므로 요청 본문이 필요 없다(#151-4).
    perform(
        post("/api/v1/auth/email/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isForbidden(),
        "AUTH_ONBOARDING_REQUIRED",
        "auth-email-verification-code-onboarding-required",
        ApiDocsTags.AUTH,
        EMAIL_CODE_SUMMARY,
        EMAIL_CODE_DESCRIPTION,
        EMAIL_CODE_403);

    // 재발송 간격 미달 → 429 (정식 ACTIVE 사용자, 첫 발송 성공 직후 즉시 재요청)
    String resendAccess = onboardCompletely("err-resend");
    mockMvc
        .perform(
            post("/api/v1/auth/email/verification-code")
                .header(HttpHeaders.AUTHORIZATION, bearer(resendAccess))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + emailFor("err-resend") + "\"}"))
        .andExpect(status().isOk());
    perform(
        post("/api/v1/auth/email/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(resendAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + emailFor("err-resend") + "\"}"),
        status().isTooManyRequests(),
        "TOO_MANY_REQUESTS",
        "auth-email-verification-code-rate-limited",
        ApiDocsTags.AUTH,
        EMAIL_CODE_SUMMARY,
        EMAIL_CODE_DESCRIPTION,
        EMAIL_CODE_429);

    // 메일 발송 실패(provider 장애·타임아웃) → 502, 챌린지 미저장 (정식 ACTIVE 사용자)
    String smtpAccess = onboardCompletely("err-smtp");
    String smtpEmail = emailFor("err-smtp");
    doThrow(new EmailDispatchException(new RuntimeException("smtp down")))
        .when(emailSender)
        .send(eq(smtpEmail), any());
    perform(
        post("/api/v1/auth/email/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(smtpAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + smtpEmail + "\"}"),
        status().isBadGateway(),
        "UPSTREAM_ERROR",
        "auth-email-verification-code-dispatch-failed",
        ApiDocsTags.AUTH,
        EMAIL_CODE_SUMMARY,
        EMAIL_CODE_DESCRIPTION,
        EMAIL_CODE_502);

    // ===== email verify (정식 ACTIVE 토큰 전용 — #192) =====
    perform(
        post("/api/v1/auth/email/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + emailFor("err-active") + "\",\"code\":\"000000\"}"),
        status().isUnprocessableEntity(),
        "AUTH_EMAIL_VERIFICATION_FAILED",
        "auth-email-verify-failed",
        ApiDocsTags.AUTH,
        EMAIL_VERIFY_SUMMARY,
        EMAIL_VERIFY_DESCRIPTION,
        EMAIL_VERIFY_422);

    perform(
        post("/api/v1/auth/email/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"\",\"code\":\"123456\"}"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-email-verify-invalid-input",
        ApiDocsTags.AUTH,
        EMAIL_VERIFY_SUMMARY,
        EMAIL_VERIFY_DESCRIPTION,
        EMAIL_VERIFY_400);

    assertError(
        mockMvc,
        post("/api/v1/auth/email/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST");
    perform(
        post("/api/v1/auth/email/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-email-verify-malformed",
        ApiDocsTags.AUTH,
        EMAIL_VERIFY_SUMMARY,
        EMAIL_VERIFY_DESCRIPTION,
        EMAIL_VERIFY_400);

    perform(
        post("/api/v1/auth/email/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "auth-email-verify-unauthenticated",
        ApiDocsTags.AUTH,
        EMAIL_VERIFY_SUMMARY,
        EMAIL_VERIFY_DESCRIPTION,
        EMAIL_VERIFY_401);

    perform(
        post("/api/v1/auth/email/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "auth-email-verify-token-expired",
        ApiDocsTags.AUTH,
        EMAIL_VERIFY_SUMMARY,
        EMAIL_VERIFY_DESCRIPTION,
        EMAIL_VERIFY_401);

    // 온보딩 미완료(온보딩 토큰) 호출 → 정식 토큰 필요 403 (#192 반전). 인가는 필터 체인에서 끝나 본문이 필요 없다.
    perform(
        post("/api/v1/auth/email/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isForbidden(),
        "AUTH_ONBOARDING_REQUIRED",
        "auth-email-verify-onboarding-required",
        ApiDocsTags.AUTH,
        EMAIL_VERIFY_SUMMARY,
        EMAIL_VERIFY_DESCRIPTION,
        EMAIL_VERIFY_403);

    // 코드 불일치 누적 → 시도 상한 초과 429 (정식 ACTIVE 사용자, 오입력 maxAttempts회째에 거절)
    String attemptsAccess = onboardCompletely("err-attempts");
    String attemptsEmail = emailFor("err-attempts");
    mockMvc
        .perform(
            post("/api/v1/auth/email/verification-code")
                .header(HttpHeaders.AUTHORIZATION, bearer(attemptsAccess))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + attemptsEmail + "\"}"))
        .andExpect(status().isOk());
    for (int i = 0; i < emailProperties.getMaxAttempts() - 1; i++) {
      mockMvc
          .perform(
              post("/api/v1/auth/email/verify")
                  .header(HttpHeaders.AUTHORIZATION, bearer(attemptsAccess))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"" + attemptsEmail + "\",\"code\":\"000000\"}"))
          .andExpect(status().isUnprocessableEntity());
    }
    perform(
        post("/api/v1/auth/email/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(attemptsAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + attemptsEmail + "\",\"code\":\"000000\"}"),
        status().isTooManyRequests(),
        "TOO_MANY_REQUESTS",
        "auth-email-verify-rate-limited",
        ApiDocsTags.AUTH,
        EMAIL_VERIFY_SUMMARY,
        EMAIL_VERIFY_DESCRIPTION,
        EMAIL_VERIFY_429);

    // ===== onboarding =====
    perform(
        post("/api/v1/auth/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(onboardingBlankGender()),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-onboarding-invalid-input",
        ApiDocsTags.AUTH,
        ONBOARDING_SUMMARY,
        ONBOARDING_DESCRIPTION,
        ONBOARDING_400);

    assertError(
        mockMvc,
        post("/api/v1/auth/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST");
    perform(
        post("/api/v1/auth/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-onboarding-malformed",
        ApiDocsTags.AUTH,
        ONBOARDING_SUMMARY,
        ONBOARDING_DESCRIPTION,
        ONBOARDING_400);

    perform(
        post("/api/v1/auth/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "auth-onboarding-unauthenticated",
        ApiDocsTags.AUTH,
        ONBOARDING_SUMMARY,
        ONBOARDING_DESCRIPTION,
        ONBOARDING_401);

    perform(
        post("/api/v1/auth/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "auth-onboarding-token-expired",
        ApiDocsTags.AUTH,
        ONBOARDING_SUMMARY,
        ONBOARDING_DESCRIPTION,
        ONBOARDING_401);

    // 약관 미동의(PENDING) 상태로 온보딩 제출 → 약관 동의 선행 안내 422 (이메일 인증 안내보다 약관 동의가 먼저)
    perform(
        post("/api/v1/auth/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(onboardingJson()),
        status().isUnprocessableEntity(),
        "AUTH_TERMS_AGREEMENT_REQUIRED",
        "auth-onboarding-terms-required",
        ApiDocsTags.AUTH,
        ONBOARDING_SUMMARY,
        ONBOARDING_DESCRIPTION,
        ONBOARDING_422);

    // #192: 온보딩에서 이메일 인증 선행 게이트가 제거됐다(AUTH_EMAIL_NOT_VERIFIED 폐지) — 약관만 동의하면 곧바로 온보딩 가능.

    // 이미 온보딩 완료한 사용자 재요청 → 409
    perform(
        post("/api/v1/auth/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content(onboardingJson()),
        status().isConflict(),
        "AUTH_ONBOARDING_ALREADY_COMPLETED",
        "auth-onboarding-already-completed",
        ApiDocsTags.AUTH,
        ONBOARDING_SUMMARY,
        ONBOARDING_DESCRIPTION,
        ONBOARDING_409);

    // ===== reissue =====
    perform(
        post("/api/v1/auth/reissue")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"\"}"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-reissue-invalid-input",
        ApiDocsTags.AUTH,
        REISSUE_SUMMARY,
        REISSUE_DESCRIPTION,
        REISSUE_400);

    perform(
        post("/api/v1/auth/reissue")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"rt_does_not_exist\"}"),
        status().isUnauthorized(),
        "AUTH_INVALID_REFRESH_TOKEN",
        "auth-reissue-invalid-token",
        ApiDocsTags.AUTH,
        REISSUE_SUMMARY,
        REISSUE_DESCRIPTION,
        REISSUE_401);

    assertError(
        mockMvc,
        post("/api/v1/auth/reissue")
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST");
    perform(
        post("/api/v1/auth/reissue").contentType(MediaType.APPLICATION_JSON),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-reissue-malformed",
        ApiDocsTags.AUTH,
        REISSUE_SUMMARY,
        REISSUE_DESCRIPTION,
        REISSUE_400);

    // ===== logout =====
    perform(
        post("/api/v1/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "auth-logout-unauthenticated",
        ApiDocsTags.AUTH,
        LOGOUT_SUMMARY,
        LOGOUT_DESCRIPTION,
        LOGOUT_401);

    perform(
        post("/api/v1/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isForbidden(),
        "AUTH_ONBOARDING_REQUIRED",
        "auth-logout-onboarding-required",
        ApiDocsTags.AUTH,
        LOGOUT_SUMMARY,
        LOGOUT_DESCRIPTION,
        LOGOUT_403);

    perform(
        post("/api/v1/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"\"}"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-logout-invalid-input",
        ApiDocsTags.AUTH,
        LOGOUT_SUMMARY,
        LOGOUT_DESCRIPTION,
        LOGOUT_400);

    assertError(
        mockMvc,
        post("/api/v1/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST");
    perform(
        post("/api/v1/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-logout-malformed",
        ApiDocsTags.AUTH,
        LOGOUT_SUMMARY,
        LOGOUT_DESCRIPTION,
        LOGOUT_400);

    perform(
        post("/api/v1/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "auth-logout-token-expired",
        ApiDocsTags.AUTH,
        LOGOUT_SUMMARY,
        LOGOUT_DESCRIPTION,
        LOGOUT_401);

    // ===== GET /users/me =====
    perform(
        get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN)),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "user-get-me-unauthenticated",
        ApiDocsTags.USERS,
        ME_SUMMARY,
        ME_DESCRIPTION,
        ME_401);

    perform(
        get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(pendingToken)),
        status().isForbidden(),
        "AUTH_ONBOARDING_REQUIRED",
        "user-get-me-onboarding-required",
        ApiDocsTags.USERS,
        ME_SUMMARY,
        ME_DESCRIPTION,
        ME_403);

    perform(
        get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "user-get-me-token-expired",
        ApiDocsTags.USERS,
        ME_SUMMARY,
        ME_DESCRIPTION,
        ME_401);

    // ===== PATCH /users/me =====
    perform(
        patch("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"birthDate\":\"2999-01-01\"}"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "user-patch-me-invalid-input",
        ApiDocsTags.USERS,
        PATCH_ME_SUMMARY,
        PATCH_ME_DESCRIPTION,
        PATCH_ME_400);

    perform(
        patch("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"gender\":\"UNKNOWN\"}"),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "user-patch-me-malformed",
        ApiDocsTags.USERS,
        PATCH_ME_SUMMARY,
        PATCH_ME_DESCRIPTION,
        PATCH_ME_400);

    // 401/403은 시큐리티 필터가 본문 파싱 전에 끊는다 — 요청 본문 없이도 같은 에러다(#151-4).
    perform(
        patch("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "user-patch-me-unauthenticated",
        ApiDocsTags.USERS,
        PATCH_ME_SUMMARY,
        PATCH_ME_DESCRIPTION,
        PATCH_ME_401);

    perform(
        patch("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "user-patch-me-token-expired",
        ApiDocsTags.USERS,
        PATCH_ME_SUMMARY,
        PATCH_ME_DESCRIPTION,
        PATCH_ME_401);

    perform(
        patch("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isForbidden(),
        "AUTH_ONBOARDING_REQUIRED",
        "user-patch-me-onboarding-required",
        ApiDocsTags.USERS,
        PATCH_ME_SUMMARY,
        PATCH_ME_DESCRIPTION,
        PATCH_ME_403);

    perform(
        patch("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(ghostToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"marketingAgreed\":true}"),
        status().isNotFound(),
        "USER_NOT_FOUND",
        "user-patch-me-not-found",
        ApiDocsTags.USERS,
        PATCH_ME_SUMMARY,
        PATCH_ME_DESCRIPTION,
        PATCH_ME_404);

    // ===== DELETE /users/me =====
    perform(
        delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN)),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "user-withdraw-unauthenticated",
        ApiDocsTags.USERS,
        WITHDRAW_SUMMARY,
        WITHDRAW_DESCRIPTION,
        WITHDRAW_401);

    perform(
        delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "user-withdraw-token-expired",
        ApiDocsTags.USERS,
        WITHDRAW_SUMMARY,
        WITHDRAW_DESCRIPTION,
        WITHDRAW_401);

    perform(
        delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(ghostToken)),
        status().isNotFound(),
        "USER_NOT_FOUND",
        "user-withdraw-not-found",
        ApiDocsTags.USERS,
        WITHDRAW_SUMMARY,
        WITHDRAW_DESCRIPTION,
        WITHDRAW_404);

    mockMvc
        .perform(
            delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(withdrawAccess)))
        .andExpect(status().isNoContent());

    perform(
        get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(withdrawAccess)),
        status().isNotFound(),
        "USER_NOT_FOUND",
        "user-get-me-not-found",
        ApiDocsTags.USERS,
        ME_SUMMARY,
        ME_DESCRIPTION,
        ME_404);

    perform(
        delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(withdrawAccess)),
        status().isConflict(),
        "USER_ALREADY_WITHDRAWN",
        "user-withdraw-already-withdrawn",
        ApiDocsTags.USERS,
        WITHDRAW_SUMMARY,
        WITHDRAW_DESCRIPTION,
        WITHDRAW_409);
  }

  // ---- helpers ----

  /**
   * 에러 스니펫 1건. summary·description·tag는 <b>성공 스니펫과 같은 상수</b>를 받아야 한다 — 생성기가 같은 {@code (path,
   * method)} 모델의 문구 중 첫 non-blank 하나만 채택하고 그 순서가 파일 순회에 좌우되기 때문이다. {@code errorCodes}는 오퍼레이션+status
   * 단위 상수다.
   */
  private void perform(
      MockHttpServletRequestBuilder request,
      ResultMatcher expectedStatus,
      String expectedCode,
      String identifier,
      String tag,
      String summary,
      String description,
      String... errorCodes)
      throws Exception {
    mockMvc
        .perform(request)
        .andExpect(expectedStatus)
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(expectedCode))
        .andDo(errorSnippet(identifier, tag, summary, description, errorCodes));
  }

  // ---- 성공 응답/요청 필드 기술자 ----

  private static List<FieldDescriptor> socialLoginRequestFields() {
    return List.of(
        enumField(
            "provider",
            Provider.class,
            "소셜 제공자(필수). 누락은 400 INVALID_INPUT, 허용 외 문자열은 400 MALFORMED_REQUEST"),
        optField("idToken", JsonFieldType.STRING, "Google OIDC ID 토큰 — GOOGLE 필수, APPLE 미사용"),
        optField(
            "authorizationCode",
            JsonFieldType.STRING,
            "Apple 1회용 인가코드(약 5분 만료) — APPLE 필수, GOOGLE 미사용"),
        optField(
            "email",
            JsonFieldType.STRING,
            "앱이 네이티브 SDK에서 받은 이메일(선택 — 최초 로그인에서는 사실상 필수, 토큰 email 클레임과 교차 검증). 재로그인 값은 무시한다(#192)"),
        optField(
            "name",
            JsonFieldType.STRING,
            "앱이 네이티브 SDK에서 받은 표시 이름(선택 — 최초 로그인에서만 캡처, 검증 없이 신뢰). Apple은 최초 1회만 제공한다(#192)"));
  }

  private static List<FieldDescriptor> socialLoginResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field(
            "data.onboardingRequired",
            JsonFieldType.BOOLEAN,
            "온보딩 필요 여부 — 신규·온보딩 미완료는 true, 기존 ACTIVE는 false"),
        enumField(
            "data.status",
            UserStatus.class,
            "사용자 상태 — 클라이언트 재개 지점 분기. 이 응답에는 PENDING·TERMS_AGREED·ACTIVE만 나온다(WITHDRAWN 계정은 로그인되지 않는다)"),
        field("data.email", JsonFieldType.STRING, "사용자 이메일(provider 진본) — 모든 분기에서 프리필용 반환(#192)"),
        optField(
            "data.name",
            JsonFieldType.STRING,
            "사용자 이름(단일 name) — 모든 분기에서 프리필용 반환. 아직 캡처된 이름이 없으면 null이다(#192)"),
        codeField("data.tokenType", TOKEN_TYPES, "토큰 타입 — 항상 Bearer"),
        field(
            "data.accessToken",
            JsonFieldType.STRING,
            "access 토큰(JWT). 온보딩 미완료면 온보딩 전용 스코프(ROLE_ONBOARDING)다"),
        optField(
            "data.refreshToken", JsonFieldType.STRING, "refresh 토큰(불투명). 온보딩 미완료 응답에서는 null이다"),
        field("data.expiresIn", JsonFieldType.NUMBER, "access 토큰 만료까지 초(온보딩 1800 / 정식 3600)"),
        errorNull());
  }

  private static List<FieldDescriptor> termsRequestFields() {
    return List.of(
        field("termsOfServiceAgreed", JsonFieldType.BOOLEAN, "이용약관 동의(필수). false면 422"),
        field("privacyPolicyAgreed", JsonFieldType.BOOLEAN, "개인정보처리방침 동의(필수). false면 422"),
        optField("marketingAgreed", JsonFieldType.BOOLEAN, "마케팅 수신 동의(선택, 미전송이면 false)"));
  }

  private static List<FieldDescriptor> termsResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        enumField("data.status", UserStatus.class, "전이 후 상태 — 이 응답에서는 항상 TERMS_AGREED"),
        field("data.termsOfServiceAgreed", JsonFieldType.BOOLEAN, "이용약관 동의 여부"),
        field("data.privacyPolicyAgreed", JsonFieldType.BOOLEAN, "개인정보처리방침 동의 여부"),
        field("data.marketingAgreed", JsonFieldType.BOOLEAN, "마케팅 수신 동의 여부"),
        field("data.agreedAt", JsonFieldType.STRING, "동의 시각(ISO-8601 UTC)"),
        errorNull());
  }

  private static List<FieldDescriptor> emailCodeRequestFields() {
    return List.of(field("email", JsonFieldType.STRING, "인증번호를 받을 이메일(필수, 이메일 형식)"));
  }

  private static List<FieldDescriptor> emailCodeResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.email", JsonFieldType.STRING, "마스킹된 이메일(예: mi***@example.com)"),
        field("data.expiresIn", JsonFieldType.NUMBER, "인증번호 만료까지 초"),
        errorNull());
  }

  private static List<FieldDescriptor> emailVerifyRequestFields() {
    return List.of(
        field("email", JsonFieldType.STRING, "인증번호를 발송한 이메일과 일치(필수)"),
        field("code", JsonFieldType.STRING, "발송된 인증번호(필수, 빈값 불가)"));
  }

  private static List<FieldDescriptor> emailVerifyResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.email", JsonFieldType.STRING, "마스킹된 이메일"),
        field("data.verified", JsonFieldType.BOOLEAN, "검증 완료 여부 — 성공 응답은 항상 true"),
        errorNull());
  }

  private static List<FieldDescriptor> onboardingRequestFields() {
    return List.of(
        enumField("gender", Gender.class, "성별(필수). 빈값·목록 밖 값은 400 INVALID_INPUT"),
        field("birthDate", JsonFieldType.STRING, "생년월일 YYYY-MM-DD(필수, 과거 날짜만)"),
        codeField(
            "country",
            COUNTRY_CODES,
            "국적 ISO 3166-1 alpha-2 코드(필수). 값 목록을 내려주는 API가 없어 여기 나열한 15개가 지원 코드의 전부다(countries 참조 시드)"),
        optEnumField(
            "occupation",
            Occupation.class,
            "직업(선택 — 미전송·null이면 저장하지 않고 프로필 응답에서 필드 자체가 생략된다, #187)"),
        enumField("visaType", VisaType.class, "비자정보(필수). API는 상수명, DB 저장은 표시 라벨"),
        optCodeField("lang", LANG_CODES, "표시 언어 ISO 639-1 소문자(선택 — 미전송이면 미설정으로 두고 표시 시 en으로 폴백)"));
  }

  private static List<FieldDescriptor> refreshTokenRequestField(String description) {
    return List.of(field("refreshToken", JsonFieldType.STRING, description));
  }

  private static List<FieldDescriptor> reissueResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        codeField("data.tokenType", TOKEN_TYPES, "토큰 타입 — 항상 Bearer"),
        field("data.accessToken", JsonFieldType.STRING, "새 access 토큰(JWT)"),
        field(
            "data.refreshToken", JsonFieldType.STRING, "새 refresh 토큰(회전 — 제출한 토큰은 ROTATED로 폐기된다)"),
        field("data.expiresIn", JsonFieldType.NUMBER, "access 토큰 만료까지 초(3600)"),
        errorNull());
  }

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

  private void agreeTerms(String token) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/terms")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(termsJson(true, true, false)))
        .andExpect(status().isOk());
  }

  /** 신규 소셜 로그인 → 약관 동의 → 온보딩 완료까지 수행하고 정식 access 토큰을 돌려준다(이메일 인증은 온보딩과 분리 — #192). */
  private String onboardCompletely(String subject) throws Exception {
    String onboardingToken = read(socialLogin(subject), "data", "accessToken");
    agreeTerms(onboardingToken);
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

  private String read(String json, String... path) throws Exception {
    JsonNode node = objectMapper.readTree(json);
    for (String key : path) {
      node = node.path(key);
    }
    return node.asText();
  }

  private static String emailFor(String subject) {
    return subject + "@mail.example";
  }

  private static String termsJson(boolean terms, boolean privacy, boolean marketing) {
    return "{\"termsOfServiceAgreed\":"
        + terms
        + ",\"privacyPolicyAgreed\":"
        + privacy
        + ",\"marketingAgreed\":"
        + marketing
        + "}";
  }

  private static String onboardingJson() {
    // 이름·이메일은 소셜 로그인 시점에 캡처되므로 온보딩 본문에 없다(#192).
    return """
        {
          "gender": "MALE",
          "birthDate": "1990-01-01",
          "country": "KR",
          "occupation": "UNDERGRADUATE_STUDENT",
          "visaType": "SHORT_TERM_VISIT",
          "lang": "ko"
        }
        """;
  }

  private static String onboardingBlankGender() {
    // 필수 gender 빈값 → INVALID_INPUT(@NotBlank).
    return """
        {
          "gender": "",
          "birthDate": "1990-01-01",
          "country": "KR",
          "occupation": "UNDERGRADUATE_STUDENT",
          "visaType": "SHORT_TERM_VISIT"
        }
        """;
  }
}
