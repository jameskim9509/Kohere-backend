package com.kohere.auth;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails;
import static com.kohere.docs.ApiDocsErrors.assertError;
import static com.kohere.docs.ApiDocsErrors.errorSnippet;
import static com.kohere.docs.ApiDocsFields.errorNull;
import static com.kohere.docs.ApiDocsFields.field;
import static com.kohere.docs.DocsTokens.bearer;
import static com.kohere.docs.DocsTokens.expiredAccessToken;
import static com.kohere.docs.UserProfileDocsFields.ME_DESCRIPTION;
import static com.kohere.docs.UserProfileDocsFields.ME_SUMMARY;
import static com.kohere.docs.UserProfileDocsFields.PATCH_ME_422;
import static com.kohere.docs.UserProfileDocsFields.PATCH_ME_DESCRIPTION;
import static com.kohere.docs.UserProfileDocsFields.PATCH_ME_SUMMARY;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kohere.TestcontainersConfiguration;
import com.kohere.auth.application.PhoneVerificationProperties;
import com.kohere.auth.domain.AppleAuthClient;
import com.kohere.auth.domain.BusinessRegistryVerifier;
import com.kohere.auth.domain.BusinessVerificationUpstreamException;
import com.kohere.auth.domain.InvalidSocialTokenException;
import com.kohere.auth.domain.OidcTokenVerifier;
import com.kohere.auth.domain.OidcUser;
import com.kohere.auth.domain.SmsDispatchException;
import com.kohere.auth.domain.VerificationEmailSender;
import com.kohere.auth.domain.VerificationSmsSender;
import com.kohere.common.security.JwtProperties;
import com.kohere.docs.ApiDocsTags;
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
 * 임대인 트랙(ADR-0034) Spring REST Docs 스니펫 생성 테스트 — 연락처 SMS 인증(발송·확인)·임대인 온보딩의 성공 응답과, 온보딩과 분리된
 * 사업자등록번호 검증(온보딩 완료 임대인 전용, 무상태)의 성공·에러 응답을 스펙(01-auth-onboarding.md §4-1·§4-2·§5-1·§5-2)대로 {@code
 * build/generated-snippets}에 생성한다. 소셜 OIDC는 가짜 주입, SMS 발송({@link VerificationSmsSender})·사업자번호 외부
 * 검증({@link BusinessRegistryVerifier})은 모킹하고 Security·JPA·Redis·JWT는 실제 구동한다.
 *
 * <p>임대인 흐름: 소셜로그인(PENDING) → 약관 동의(TERMS_AGREED) → 연락처 인증(코드 발송·확인) → 임대인
 * 온보딩(ACTIVE·userType=LANDLORD). 사업자등록번호 검증은 온보딩에서 분리되어, 온보딩을 마친(ACTIVE) 임대인이 정식 토큰(ROLE_USER)으로 별도
 * 호출한다(§5-1). SMS 발송 모킹으로 인증번호를 캡처한다.
 *
 * <p><b>문서 규약(#151)</b> — {@code GET /users/me}는 {@code AuthOnboardingDocsTest}(세입자 예시)와 <b>같은
 * 오퍼레이션</b>이라 문구·필드 기술자를 {@code com.kohere.docs.UserProfileDocsFields}에서 공유한다. 임대인 예시의 identifier가
 * {@code user-get-me-landlord}인 것도 그 때문이다 — operationId는 스니펫 identifier들의 공통 접두사라, 예전 이름 {@code
 * users-me-landlord}는 {@code user-get-me*}와 겹치는 접두사가 {@code user}뿐이라 operationId를 {@code user}로
 * 붕괴시켰다.
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LandlordOnboardingDocsTest {

  private static final String INVALID_SOCIAL_TOKEN = "invalid-social-token";
  private static final String MALFORMED_BODY = "{ \"oops\" }";
  private static final String PHONE = "01012345678";

  /** SMS 인증을 거치지 않은 번호 — 임대인 프로필 연락처 변경의 422 예시에 쓴다(어떤 테스트도 이 번호로 발송하지 않는다). */
  private static final String UNVERIFIED_PHONE = "01033332222";

  private static final String BIRTH_DATE = "1988-05-20";
  private static final String BIZ_NUMBER = "1234567890";

  /** 세입자 온보딩 본문 — 임대인 전용 API의 역할 거부(403 FORBIDDEN) 예시용 계정을 만들 때만 쓴다. */
  private static final String TENANT_ONBOARDING_BODY =
      """
      {
        "gender": "FEMALE",
        "birthDate": "1995-03-11",
        "country": "VN",
        "visaType": "SHORT_TERM_VISIT"
      }
      """;

  // ---- 오퍼레이션 문구·에러코드 상수(규약 1·3·4·11) ----

  private static final String PHONE_CODE_SUMMARY = "연락처 인증번호 발송";

  private static final String PHONE_CODE_DESCRIPTION =
      """
      입력한 휴대폰 번호로 SMS 인증번호를 동기 발송하고 챌린지를 저장한다. 응답 `phoneNumber`는 마스킹된다(예 `010-****-5678`).

      인증: 필수(임대인 트랙). 온보딩 토큰과 정식 토큰을 <b>둘 다</b> 허용한다 — 온보딩(US-1-10)과 정식 회원의 프로필 연락처 변경(US-1-5)이 같은 엔드포인트를 쓰기 때문이다(ADR-0034 §6·§8).

      - 약관 동의(`TERMS_AGREED`)가 선행돼야 한다 — `PENDING` 상태 호출은 422다.
      - 발송이 실패하면(SMS provider 장애·타임아웃) 챌린지를 저장하지 않고 502다.
      - 재발송 간격을 채우지 않은 재요청은 429다.
      - `expiresIn`은 인증번호 만료까지의 초다.

      에러: 400 `INVALID_INPUT`·`MALFORMED_REQUEST`, 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED`, 422 `AUTH_TERMS_AGREEMENT_REQUIRED`, 429 `TOO_MANY_REQUESTS`, 502 `UPSTREAM_ERROR`.
      """;

  private static final String[] PHONE_CODE_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  private static final String[] PHONE_CODE_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  private static final String[] PHONE_CODE_422 = {"AUTH_TERMS_AGREEMENT_REQUIRED"};
  private static final String[] PHONE_CODE_429 = {"TOO_MANY_REQUESTS"};
  private static final String[] PHONE_CODE_502 = {"UPSTREAM_ERROR"};

  private static final String PHONE_VERIFY_SUMMARY = "연락처 인증번호 확인";

  private static final String PHONE_VERIFY_DESCRIPTION =
      """
      발송된 인증번호를 확인해 연락처를 검증 완료(VERIFIED)로 마킹한다. 임대인 온보딩(§5-2)·프로필 연락처 변경(§9)의 선행 단계다.

      인증: 필수(임대인 트랙). 온보딩 토큰과 정식 토큰을 둘 다 허용한다.

      - `phoneNumber`는 인증번호를 발송한 번호와 일치해야 한다.
      - 챌린지 부재·만료·코드 불일치는 모두 422 하나로 응답한다(어느 쪽인지 구분해 주지 않는다).
      - 코드 불일치가 시도 상한까지 누적되면 429로 잠긴다.

      에러: 400 `INVALID_INPUT`·`MALFORMED_REQUEST`, 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED`, 422 `AUTH_PHONE_VERIFICATION_FAILED`, 429 `TOO_MANY_REQUESTS`.
      """;

  private static final String[] PHONE_VERIFY_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  private static final String[] PHONE_VERIFY_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  private static final String[] PHONE_VERIFY_422 = {"AUTH_PHONE_VERIFICATION_FAILED"};
  private static final String[] PHONE_VERIFY_429 = {"TOO_MANY_REQUESTS"};

  private static final String BUSINESS_SUMMARY = "사업자등록번호 검증";

  private static final String BUSINESS_DESCRIPTION =
      """
      사업자등록번호를 외부 registry로 검증한다. 결과를 저장하지 않는 무상태 검증이라 응답 본문으로만 돌려주며, 번호는 마스킹된다(예 `****567890`).

      인증: 필수(정식 토큰 — `ACTIVE`·`ROLE_USER`, 임대인 전용). 온보딩 흐름이 아니라서 온보딩 토큰으로 호출하면 403이다.

      - 온보딩 제출(§5-2)에는 포함되지 않는다 — 온보딩을 마친 임대인이 매물 등록 시점에 따로 호출한다.
      - 허용 형식은 숫자 10자리와 하이픈 형식(`123-45-67890`) <b>둘 다</b>다(어댑터가 숫자만 정규화해 대조한다). 형식 위반은 외부 호출 전에 400 `INVALID_INPUT`으로 거른다.
      - 미등록·휴폐업·진위 실패는 422, 외부 검증 API 장애·타임아웃은 502이며 어느 쪽도 결과를 저장하지 않는다.

      에러: 400 `INVALID_INPUT`·`MALFORMED_REQUEST`, 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED`, 403 `AUTH_ONBOARDING_REQUIRED`·`FORBIDDEN`, 422 `AUTH_BUSINESS_NUMBER_VERIFICATION_FAILED`, 502 `UPSTREAM_ERROR`.
      """;

  private static final String[] BUSINESS_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  private static final String[] BUSINESS_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  // 403이 둘로 갈린다 — 온보딩 토큰(필터)은 AUTH_ONBOARDING_REQUIRED, 정식 토큰이지만 세입자(서비스)는 FORBIDDEN이다.
  private static final String[] BUSINESS_403 = {"AUTH_ONBOARDING_REQUIRED", "FORBIDDEN"};
  private static final String[] BUSINESS_422 = {"AUTH_BUSINESS_NUMBER_VERIFICATION_FAILED"};
  private static final String[] BUSINESS_502 = {"UPSTREAM_ERROR"};

  private static final String LANDLORD_ONBOARDING_SUMMARY = "임대인 온보딩 제출";

  private static final String LANDLORD_ONBOARDING_DESCRIPTION =
      """
      연락처·생년월일을 제출해 `TERMS_AGREED`를 `ACTIVE`로 전이하고 `userType`을 `LANDLORD`로 확정한 뒤 정식 토큰을 발급한다.

      인증: 필수(온보딩 토큰, 상태 `TERMS_AGREED`). 약관 미동의(`PENDING`)면 422, 이미 완료(`ACTIVE`)면 409다.

      - 요청은 `phoneNumber`·`birthDate` 둘뿐이다 — `phoneNumber`는 빈값만 막고(번호 형식 검증은 없다), `birthDate`는 필수이며 과거 날짜만 허용한다(미래면 400).
      - `phoneNumber`는 사전 SMS 인증(§4-1·§4-2)한 번호와 일치해야 한다 — 미인증·불일치는 422 `AUTH_PHONE_NOT_VERIFIED`다(약관 검사가 연락처 검사보다 먼저다).
      - 이름·이메일은 소셜 로그인 시점에 확정돼 여기서 받지 않고, 사업자등록번호도 받지 않는다(온보딩 후 별도 검증 §5-1).
      - 국적·표시 언어는 서버가 `KR`·`ko`로 고정 부여한다. 응답 `data.user`의 세입자 전용 필드(`gender`·`occupation`·`visaType`)는 필드 자체가 생략되고 `phoneNumber`는 마스킹된다.

      에러: 400 `INVALID_INPUT`·`MALFORMED_REQUEST`, 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED`, 409 `AUTH_ONBOARDING_ALREADY_COMPLETED`, 422 `AUTH_TERMS_AGREEMENT_REQUIRED`·`AUTH_PHONE_NOT_VERIFIED`.
      """;

  private static final String[] LANDLORD_ONBOARDING_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  private static final String[] LANDLORD_ONBOARDING_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  private static final String[] LANDLORD_ONBOARDING_409 = {"AUTH_ONBOARDING_ALREADY_COMPLETED"};
  private static final String[] LANDLORD_ONBOARDING_422 = {
    "AUTH_TERMS_AGREEMENT_REQUIRED", "AUTH_PHONE_NOT_VERIFIED"
  };

  // 서명이 깨진(다른 키) 액세스 토큰 — 401 UNAUTHENTICATED 를 유발하면서도 구조상 JWT 라 restdocs-api-spec 이
  // 무인증 예시에서도 bearerAuthJWT 보안 스킴을 도출하게 한다(AuthOnboardingDocsTest 와 동일 의도).
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
        return new OidcUser(provider, idToken, idToken + "@example.com");
      };
    }
  }

  @Autowired private WebApplicationContext context;
  @Autowired private JwtProperties jwtProperties;
  @Autowired private PhoneVerificationProperties phoneProperties;
  @MockitoBean private VerificationSmsSender smsSender;
  @MockitoBean private BusinessRegistryVerifier businessVerifier;
  @MockitoBean private VerificationEmailSender emailSender; // 임대인 흐름 미사용(컨텍스트 충족용)
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
    // 발송 인증번호를 연락처별로 기록(검증 단계에서 사용). 실제 SMS 발송은 하지 않는다.
    doAnswer(
            inv -> {
              sentCodes.put(inv.getArgument(0), inv.getArgument(1));
              return null;
            })
        .when(smsSender)
        .send(any(), any());
    // 정상(계속) 사업자 — 기본 검증 통과(특정 실패·장애 케이스만 개별 오버라이드)
    when(businessVerifier.verify(BIZ_NUMBER)).thenReturn(true);
  }

  @Test
  void generatesLandlordOnboardingSnippets() throws Exception {
    String onboardingToken = read(socialLogin("docs-landlord-1"), "data", "accessToken");
    agreeTerms(onboardingToken);

    // 연락처 인증번호 발송
    mockMvc
        .perform(
            post("/api/v1/auth/phone/verification-code")
                .header(HttpHeaders.AUTHORIZATION, bearer(onboardingToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + PHONE + "\"}"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "auth-phone-verification-code",
                resourceDetails()
                    .tag(ApiDocsTags.AUTH)
                    .summary(PHONE_CODE_SUMMARY)
                    .description(PHONE_CODE_DESCRIPTION),
                requestFields(phoneCodeRequestFields()),
                responseFields(phoneCodeResponseFields())));

    // 연락처 인증번호 확인
    mockMvc
        .perform(
            post("/api/v1/auth/phone/verify")
                .header(HttpHeaders.AUTHORIZATION, bearer(onboardingToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"phoneNumber\":\""
                        + PHONE
                        + "\",\"code\":\""
                        + sentCodes.get(PHONE)
                        + "\"}"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "auth-phone-verify",
                resourceDetails()
                    .tag(ApiDocsTags.AUTH)
                    .summary(PHONE_VERIFY_SUMMARY)
                    .description(PHONE_VERIFY_DESCRIPTION),
                requestFields(phoneVerifyRequestFields()),
                responseFields(phoneVerifyResponseFields())));

    // 임대인 온보딩 제출 → 정식 토큰(사업자번호는 온보딩에서 수집하지 않음)
    // 규약 13: 응답 필드를 세입자·임대인 합집합으로 합치며 세입자 전용 필드를 optional로 낮췄으므로,
    // 임대인 응답에 그 필드가 없다는 계약을 단정으로 되메운다.
    String onboardingBody =
        mockMvc
            .perform(
                post("/api/v1/auth/landlord/onboarding")
                    .header(HttpHeaders.AUTHORIZATION, bearer(onboardingToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(landlordJson(PHONE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.userType").value("LANDLORD"))
            .andExpect(jsonPath("$.data.user.gender").doesNotExist())
            .andExpect(jsonPath("$.data.user.occupation").doesNotExist())
            .andExpect(jsonPath("$.data.user.visaType").doesNotExist())
            .andDo(
                document(
                    "auth-landlord-onboarding",
                    resourceDetails()
                        .tag(ApiDocsTags.AUTH)
                        .summary(LANDLORD_ONBOARDING_SUMMARY)
                        .description(LANDLORD_ONBOARDING_DESCRIPTION),
                    requestFields(landlordOnboardingRequestFields()),
                    responseFields(onboardingResponseFields())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String accessToken = read(onboardingBody, "data", "accessToken");

    // 사업자등록번호 검증 — 온보딩 완료(ACTIVE) 임대인이 정식 토큰으로 호출하는 무상태 검증
    mockMvc
        .perform(
            post("/api/v1/auth/business/verify")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessRegistrationNumber\":\"" + BIZ_NUMBER + "\"}"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "auth-business-verify",
                resourceDetails()
                    .tag(ApiDocsTags.AUTH)
                    .summary(BUSINESS_SUMMARY)
                    .description(BUSINESS_DESCRIPTION),
                requestFields(businessVerifyRequestFields()),
                responseFields(businessVerifyResponseFields())));

    // 임대인 내 프로필 조회 — GET /users/me의 임대인 예시(세입자 예시는 AuthOnboardingDocsTest의 user-get-me).
    // 규약 13: 세입자 전용 필드가 임대인 응답에 없다는 계약을 단정으로 되메운다.
    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userType").value("LANDLORD"))
        .andExpect(jsonPath("$.data.birthDate").value(BIRTH_DATE))
        .andExpect(jsonPath("$.data.gender").doesNotExist())
        .andExpect(jsonPath("$.data.occupation").doesNotExist())
        .andExpect(jsonPath("$.data.visaType").doesNotExist())
        .andDo(
            document(
                "user-get-me-landlord",
                resourceDetails()
                    .tag(ApiDocsTags.USERS)
                    .summary(ME_SUMMARY)
                    .description(ME_DESCRIPTION),
                responseFields(meResponseFields())));

    // 임대인 내 프로필 수정 — PATCH /users/me의 임대인 예시(세입자 예시는 AuthOnboardingDocsTest의 user-patch-me).
    // 임대인이 바꿀 수 있는 건 name·phoneNumber·marketingAgreed뿐이다. 여기서는 이미 SMS 인증된 현재 번호를 그대로
    // 보내므로 재인증 게이트(§9, 미인증 422)에 걸리지 않는다.
    // 규약 13: 세입자 전용 필드가 임대인 응답에 없다는 계약을 단정으로 되메운다.
    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Kim Imdae\",\"phoneNumber\":\""
                        + PHONE
                        + "\",\"marketingAgreed\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userType").value("LANDLORD"))
        .andExpect(jsonPath("$.data.phoneNumber").value(PHONE))
        .andExpect(jsonPath("$.data.marketingAgreed").value(true))
        .andExpect(jsonPath("$.data.gender").doesNotExist())
        .andExpect(jsonPath("$.data.occupation").doesNotExist())
        .andExpect(jsonPath("$.data.visaType").doesNotExist())
        .andDo(
            document(
                "user-patch-me-landlord",
                resourceDetails()
                    .tag(ApiDocsTags.USERS)
                    .summary(PATCH_ME_SUMMARY)
                    .description(PATCH_ME_DESCRIPTION),
                requestFields(patchRequestFields()),
                responseFields(meResponseFields())));
  }

  /** 스펙의 "발생 가능한 에러"를 엔드포인트별로 실제 트리거해 스니펫으로 생성하고 status·error.code를 단정한다. */
  @Test
  void generatesLandlordOnboardingErrorSnippets() throws Exception {
    String pendingToken = read(socialLogin("err-l-pending"), "data", "accessToken"); // PENDING
    String termsToken = read(socialLogin("err-l-terms"), "data", "accessToken");
    agreeTerms(termsToken); // TERMS_AGREED(연락처 미검증, 온보딩 토큰)
    String activeAccess = onboardLandlordCompletely("err-l-active"); // 정식 ACTIVE 임대인 access

    String expiredToken = expiredAccessToken(jwtProperties);

    // ===== phone/verification-code =====
    perform(
        post("/api/v1/auth/phone/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(termsToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phoneNumber\":\"\"}"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-phone-verification-code-invalid-input",
        ApiDocsTags.AUTH,
        PHONE_CODE_SUMMARY,
        PHONE_CODE_DESCRIPTION,
        PHONE_CODE_400);

    // 문서 스니펫은 본문 없이 만든다(#151-4) — 본문 누락도 같은 MALFORMED_REQUEST 라 예시가 중복되지 않는다.
    // 「깨진 JSON 거부」계약은 스니펫 없이 단정만 남겨 회귀를 막는다.
    assertError(
        mockMvc,
        post("/api/v1/auth/phone/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(termsToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST");
    perform(
        post("/api/v1/auth/phone/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(termsToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-phone-verification-code-malformed",
        ApiDocsTags.AUTH,
        PHONE_CODE_SUMMARY,
        PHONE_CODE_DESCRIPTION,
        PHONE_CODE_400);

    // 401은 시큐리티 필터가 본문 파싱 전에 끊는다 — 요청 본문 없이도 같은 에러다(#151-4).
    perform(
        post("/api/v1/auth/phone/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "auth-phone-verification-code-unauthenticated",
        ApiDocsTags.AUTH,
        PHONE_CODE_SUMMARY,
        PHONE_CODE_DESCRIPTION,
        PHONE_CODE_401);

    perform(
        post("/api/v1/auth/phone/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "auth-phone-verification-code-token-expired",
        ApiDocsTags.AUTH,
        PHONE_CODE_SUMMARY,
        PHONE_CODE_DESCRIPTION,
        PHONE_CODE_401);

    // 약관 미동의(PENDING) 상태 발송 → 약관 동의 선행 안내 422
    perform(
        post("/api/v1/auth/phone/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phoneNumber\":\"" + PHONE + "\"}"),
        status().isUnprocessableEntity(),
        "AUTH_TERMS_AGREEMENT_REQUIRED",
        "auth-phone-verification-code-terms-required",
        ApiDocsTags.AUTH,
        PHONE_CODE_SUMMARY,
        PHONE_CODE_DESCRIPTION,
        PHONE_CODE_422);

    // 재발송 간격 미달 → 429 (첫 발송 성공 직후 즉시 재요청)
    String resendToken = read(socialLogin("err-l-resend"), "data", "accessToken");
    agreeTerms(resendToken);
    mockMvc
        .perform(
            post("/api/v1/auth/phone/verification-code")
                .header(HttpHeaders.AUTHORIZATION, bearer(resendToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + PHONE + "\"}"))
        .andExpect(status().isOk());
    perform(
        post("/api/v1/auth/phone/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(resendToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phoneNumber\":\"" + PHONE + "\"}"),
        status().isTooManyRequests(),
        "TOO_MANY_REQUESTS",
        "auth-phone-verification-code-rate-limited",
        ApiDocsTags.AUTH,
        PHONE_CODE_SUMMARY,
        PHONE_CODE_DESCRIPTION,
        PHONE_CODE_429);

    // SMS 발송 실패(provider 장애·타임아웃) → 502, 챌린지 미저장
    String smsFailToken = read(socialLogin("err-l-sms"), "data", "accessToken");
    agreeTerms(smsFailToken);
    String smsFailPhone = "01099990000";
    doThrow(new SmsDispatchException(new RuntimeException("solapi down")))
        .when(smsSender)
        .send(eq(smsFailPhone), any());
    perform(
        post("/api/v1/auth/phone/verification-code")
            .header(HttpHeaders.AUTHORIZATION, bearer(smsFailToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phoneNumber\":\"" + smsFailPhone + "\"}"),
        status().isBadGateway(),
        "UPSTREAM_ERROR",
        "auth-phone-verification-code-dispatch-failed",
        ApiDocsTags.AUTH,
        PHONE_CODE_SUMMARY,
        PHONE_CODE_DESCRIPTION,
        PHONE_CODE_502);

    // US-1-5: 정식(ACTIVE) 임대인도 프로필 연락처 변경을 위해 인증번호를 받을 수 있다(온보딩 전용 아님 — 409 아님, ADR-0034 §6·§8).
    mockMvc
        .perform(
            post("/api/v1/auth/phone/verification-code")
                .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + PHONE + "\"}"))
        .andExpect(status().isOk());

    // ===== phone/verify =====
    perform(
        post("/api/v1/auth/phone/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phoneNumber\":\"" + PHONE + "\",\"code\":\"000000\"}"),
        status().isUnprocessableEntity(),
        "AUTH_PHONE_VERIFICATION_FAILED",
        "auth-phone-verify-failed",
        ApiDocsTags.AUTH,
        PHONE_VERIFY_SUMMARY,
        PHONE_VERIFY_DESCRIPTION,
        PHONE_VERIFY_422);

    perform(
        post("/api/v1/auth/phone/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phoneNumber\":\"\",\"code\":\"123456\"}"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-phone-verify-invalid-input",
        ApiDocsTags.AUTH,
        PHONE_VERIFY_SUMMARY,
        PHONE_VERIFY_DESCRIPTION,
        PHONE_VERIFY_400);

    assertError(
        mockMvc,
        post("/api/v1/auth/phone/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST");
    perform(
        post("/api/v1/auth/phone/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-phone-verify-malformed",
        ApiDocsTags.AUTH,
        PHONE_VERIFY_SUMMARY,
        PHONE_VERIFY_DESCRIPTION,
        PHONE_VERIFY_400);

    perform(
        post("/api/v1/auth/phone/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "auth-phone-verify-unauthenticated",
        ApiDocsTags.AUTH,
        PHONE_VERIFY_SUMMARY,
        PHONE_VERIFY_DESCRIPTION,
        PHONE_VERIFY_401);

    perform(
        post("/api/v1/auth/phone/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "auth-phone-verify-token-expired",
        ApiDocsTags.AUTH,
        PHONE_VERIFY_SUMMARY,
        PHONE_VERIFY_DESCRIPTION,
        PHONE_VERIFY_401);

    // 코드 불일치 누적 → 시도 상한 초과 429
    String attemptsToken = read(socialLogin("err-l-attempts"), "data", "accessToken");
    agreeTerms(attemptsToken);
    mockMvc
        .perform(
            post("/api/v1/auth/phone/verification-code")
                .header(HttpHeaders.AUTHORIZATION, bearer(attemptsToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + PHONE + "\"}"))
        .andExpect(status().isOk());
    for (int i = 0; i < phoneProperties.getMaxAttempts() - 1; i++) {
      mockMvc
          .perform(
              post("/api/v1/auth/phone/verify")
                  .header(HttpHeaders.AUTHORIZATION, bearer(attemptsToken))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"phoneNumber\":\"" + PHONE + "\",\"code\":\"000000\"}"))
          .andExpect(status().isUnprocessableEntity());
    }
    perform(
        post("/api/v1/auth/phone/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(attemptsToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phoneNumber\":\"" + PHONE + "\",\"code\":\"000000\"}"),
        status().isTooManyRequests(),
        "TOO_MANY_REQUESTS",
        "auth-phone-verify-rate-limited",
        ApiDocsTags.AUTH,
        PHONE_VERIFY_SUMMARY,
        PHONE_VERIFY_DESCRIPTION,
        PHONE_VERIFY_429);

    // ===== business/verify (온보딩 후 ACTIVE 임대인 전용) =====
    // 형식 위반 — 정식 토큰(ACTIVE 임대인) 인가 통과 후 입력 검증 실패
    perform(
        post("/api/v1/auth/business/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"businessRegistrationNumber\":\"123\"}"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-business-verify-invalid-input",
        ApiDocsTags.AUTH,
        BUSINESS_SUMMARY,
        BUSINESS_DESCRIPTION,
        BUSINESS_400);

    assertError(
        mockMvc,
        post("/api/v1/auth/business/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST");
    perform(
        post("/api/v1/auth/business/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-business-verify-malformed",
        ApiDocsTags.AUTH,
        BUSINESS_SUMMARY,
        BUSINESS_DESCRIPTION,
        BUSINESS_400);

    perform(
        post("/api/v1/auth/business/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "auth-business-verify-unauthenticated",
        ApiDocsTags.AUTH,
        BUSINESS_SUMMARY,
        BUSINESS_DESCRIPTION,
        BUSINESS_401);

    perform(
        post("/api/v1/auth/business/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "auth-business-verify-token-expired",
        ApiDocsTags.AUTH,
        BUSINESS_SUMMARY,
        BUSINESS_DESCRIPTION,
        BUSINESS_401);

    // 온보딩 미완료(온보딩 토큰, ROLE_ONBOARDING) 호출 → 정식 토큰 필요 403.
    // 인가는 필터 체인에서 끝나므로 요청 본문이 필요 없다(#151-4).
    perform(
        post("/api/v1/auth/business/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(termsToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isForbidden(),
        "AUTH_ONBOARDING_REQUIRED",
        "auth-business-verify-onboarding-required",
        ApiDocsTags.AUTH,
        BUSINESS_SUMMARY,
        BUSINESS_DESCRIPTION,
        BUSINESS_403);

    // 온보딩을 마친 세입자(정식 토큰·ROLE_USER)가 호출 → 임대인 전용이라 403 FORBIDDEN.
    // 필터(ROLE_USER)는 통과하고 서비스의 assertLandlord가 막는 판정이라 유효 본문이 필요하다(#151-4).
    String tenantAccess = onboardTenantCompletely("err-l-tenant");
    perform(
        post("/api/v1/auth/business/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(tenantAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"businessRegistrationNumber\":\"" + BIZ_NUMBER + "\"}"),
        status().isForbidden(),
        "FORBIDDEN",
        "auth-business-verify-forbidden",
        ApiDocsTags.AUTH,
        BUSINESS_SUMMARY,
        BUSINESS_DESCRIPTION,
        BUSINESS_403);

    // 미등록·휴폐업·진위 실패 → 422 (외부 검증 결과 false)
    String failNumber = "9999999999";
    when(businessVerifier.verify(failNumber)).thenReturn(false);
    perform(
        post("/api/v1/auth/business/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"businessRegistrationNumber\":\"" + failNumber + "\"}"),
        status().isUnprocessableEntity(),
        "AUTH_BUSINESS_NUMBER_VERIFICATION_FAILED",
        "auth-business-verify-failed",
        ApiDocsTags.AUTH,
        BUSINESS_SUMMARY,
        BUSINESS_DESCRIPTION,
        BUSINESS_422);

    // 외부 검증 API 장애·타임아웃 → 502
    String upstreamNumber = "8888888888";
    when(businessVerifier.verify(upstreamNumber))
        .thenThrow(new BusinessVerificationUpstreamException(new RuntimeException("bizno down")));
    perform(
        post("/api/v1/auth/business/verify")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"businessRegistrationNumber\":\"" + upstreamNumber + "\"}"),
        status().isBadGateway(),
        "UPSTREAM_ERROR",
        "auth-business-verify-upstream-error",
        ApiDocsTags.AUTH,
        BUSINESS_SUMMARY,
        BUSINESS_DESCRIPTION,
        BUSINESS_502);

    // ===== landlord/onboarding =====
    // phoneNumber 빈값(@NotBlank) — 번호 형식 검증은 없다. birthDate 는 @NotNull·@Past 라 미래 날짜도 여기로 온다.
    perform(
        post("/api/v1/auth/landlord/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(landlordJson("")),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-landlord-onboarding-invalid-input",
        ApiDocsTags.AUTH,
        LANDLORD_ONBOARDING_SUMMARY,
        LANDLORD_ONBOARDING_DESCRIPTION,
        LANDLORD_ONBOARDING_400);

    assertError(
        mockMvc,
        post("/api/v1/auth/landlord/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST");
    perform(
        post("/api/v1/auth/landlord/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-landlord-onboarding-malformed",
        ApiDocsTags.AUTH,
        LANDLORD_ONBOARDING_SUMMARY,
        LANDLORD_ONBOARDING_DESCRIPTION,
        LANDLORD_ONBOARDING_400);

    perform(
        post("/api/v1/auth/landlord/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "auth-landlord-onboarding-unauthenticated",
        ApiDocsTags.AUTH,
        LANDLORD_ONBOARDING_SUMMARY,
        LANDLORD_ONBOARDING_DESCRIPTION,
        LANDLORD_ONBOARDING_401);

    perform(
        post("/api/v1/auth/landlord/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "auth-landlord-onboarding-token-expired",
        ApiDocsTags.AUTH,
        LANDLORD_ONBOARDING_SUMMARY,
        LANDLORD_ONBOARDING_DESCRIPTION,
        LANDLORD_ONBOARDING_401);

    // 약관 미동의(PENDING) 상태 제출 → 약관 동의 선행 안내 422(연락처 검사보다 먼저)
    perform(
        post("/api/v1/auth/landlord/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(pendingToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(landlordJson(PHONE)),
        status().isUnprocessableEntity(),
        "AUTH_TERMS_AGREEMENT_REQUIRED",
        "auth-landlord-onboarding-terms-required",
        ApiDocsTags.AUTH,
        LANDLORD_ONBOARDING_SUMMARY,
        LANDLORD_ONBOARDING_DESCRIPTION,
        LANDLORD_ONBOARDING_422);

    // 약관 동의했으나 연락처 미인증 → 422 AUTH_PHONE_NOT_VERIFIED
    String phoneNeedToken = read(socialLogin("err-l-phoneneed"), "data", "accessToken");
    agreeTerms(phoneNeedToken);
    perform(
        post("/api/v1/auth/landlord/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(phoneNeedToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(landlordJson(PHONE)),
        status().isUnprocessableEntity(),
        "AUTH_PHONE_NOT_VERIFIED",
        "auth-landlord-onboarding-phone-not-verified",
        ApiDocsTags.AUTH,
        LANDLORD_ONBOARDING_SUMMARY,
        LANDLORD_ONBOARDING_DESCRIPTION,
        LANDLORD_ONBOARDING_422);

    perform(
        post("/api/v1/auth/landlord/onboarding")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content(landlordJson(PHONE)),
        status().isConflict(),
        "AUTH_ONBOARDING_ALREADY_COMPLETED",
        "auth-landlord-onboarding-already-completed",
        ApiDocsTags.AUTH,
        LANDLORD_ONBOARDING_SUMMARY,
        LANDLORD_ONBOARDING_DESCRIPTION,
        LANDLORD_ONBOARDING_409);

    // ===== PATCH /users/me (임대인 연락처 변경) =====
    // 현재 번호와 다른 새 번호를 보내면 그 번호의 SMS 재인증 마커를 확인한다 — 미인증·불일치는 422(§9, ADR-0034).
    // 이 계정의 검증 마커는 온보딩에 쓴 PHONE이라 다른 번호로는 통과하지 못한다. 서비스 단계 판정이라 유효 본문이 필요하다.
    perform(
        patch("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(activeAccess))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phoneNumber\":\"" + UNVERIFIED_PHONE + "\"}"),
        status().isUnprocessableEntity(),
        "AUTH_PHONE_NOT_VERIFIED",
        "user-patch-me-phone-not-verified",
        ApiDocsTags.USERS,
        PATCH_ME_SUMMARY,
        PATCH_ME_DESCRIPTION,
        PATCH_ME_422);
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

  private static List<FieldDescriptor> phoneCodeRequestFields() {
    return List.of(
        field(
            "phoneNumber",
            JsonFieldType.STRING,
            "인증번호를 받을 휴대폰 번호(필수, 빈값 불가 — 번호 형식 자체를 검증하지는 않는다)"));
  }

  private static List<FieldDescriptor> phoneCodeResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.phoneNumber", JsonFieldType.STRING, "마스킹된 연락처(예: 010-****-5678)"),
        field("data.expiresIn", JsonFieldType.NUMBER, "인증번호 만료까지 초"),
        errorNull());
  }

  private static List<FieldDescriptor> phoneVerifyRequestFields() {
    return List.of(
        field("phoneNumber", JsonFieldType.STRING, "인증번호를 발송한 연락처와 일치(필수)"),
        field("code", JsonFieldType.STRING, "발송된 인증번호(필수, 빈값 불가)"));
  }

  private static List<FieldDescriptor> phoneVerifyResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.phoneNumber", JsonFieldType.STRING, "마스킹된 연락처(예: 010-****-5678)"),
        field("data.verified", JsonFieldType.BOOLEAN, "검증 완료 여부 — 성공 응답은 항상 true"),
        errorNull());
  }

  private static List<FieldDescriptor> businessVerifyRequestFields() {
    return List.of(
        field(
            "businessRegistrationNumber",
            JsonFieldType.STRING,
            "사업자등록번호(필수) — 숫자 10자리 또는 하이픈 형식(123-45-67890) 둘 다 허용한다. 어댑터가 숫자만 정규화해 대조하므로 두 형식이 동일하게 처리된다"));
  }

  private static List<FieldDescriptor> businessVerifyResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field(
            "data.businessRegistrationNumber", JsonFieldType.STRING, "마스킹된 사업자등록번호(예: ****567890)"),
        field("data.verified", JsonFieldType.BOOLEAN, "정상 사업자 검증 완료 여부 — 성공 응답은 항상 true"),
        errorNull());
  }

  private static List<FieldDescriptor> landlordOnboardingRequestFields() {
    return List.of(
        field(
            "phoneNumber",
            JsonFieldType.STRING,
            "사전 SMS 인증된 연락처와 일치(필수, 빈값 불가 — 번호 형식 자체를 검증하지는 않는다). 불일치·미인증은 422"),
        field("birthDate", JsonFieldType.STRING, "생년월일 YYYY-MM-DD(필수, 과거 날짜만 — 미래면 400)"));
  }

  private String socialLogin(String subject) throws Exception {
    // 이름은 소셜 로그인 시 캡처된다(#192) — 임대인 온보딩 응답의 name은 이 값이다.
    return mockMvc
        .perform(
            post("/api/v1/auth/social-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"provider\":\"GOOGLE\",\"idToken\":\""
                        + subject
                        + "\",\"name\":\"Kim Imdae\"}"))
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
                .content(
                    "{\"termsOfServiceAgreed\":true,\"privacyPolicyAgreed\":true,\"marketingAgreed\":false}"))
        .andExpect(status().isOk());
  }

  private void sendAndVerifyPhone(String token, String phone) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/phone/verification-code")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + phone + "\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/auth/phone/verify")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"phoneNumber\":\""
                        + phone
                        + "\",\"code\":\""
                        + sentCodes.get(phone)
                        + "\"}"))
        .andExpect(status().isOk());
  }

  /** 신규 소셜 로그인 → 약관 동의 → 연락처 인증 → 임대인 온보딩까지 수행하고 정식 access 토큰을 돌려준다(사업자번호는 온보딩과 무관). */
  private String onboardLandlordCompletely(String subject) throws Exception {
    String token = read(socialLogin(subject), "data", "accessToken");
    agreeTerms(token);
    sendAndVerifyPhone(token, PHONE);
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/landlord/onboarding")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(landlordJson(PHONE)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return read(body, "data", "accessToken");
  }

  /**
   * 신규 소셜 로그인 → 약관 동의 → <b>세입자</b> 온보딩까지 수행하고 정식 access 토큰을 돌려준다. 임대인 전용 API가 역할로 거부하는(403
   * FORBIDDEN) 예시를 만들기 위한 것이라 연락처 인증 단계가 없다 — 세입자 트랙 자체의 문서는 {@code AuthOnboardingDocsTest}가 만든다.
   */
  private String onboardTenantCompletely(String subject) throws Exception {
    String token = read(socialLogin(subject), "data", "accessToken");
    agreeTerms(token);
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/onboarding")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TENANT_ONBOARDING_BODY))
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

  private static String landlordJson(String phone) {
    // 이름은 온보딩에서 받지 않는다(소셜 로그인 캡처) — 요청 본문은 { phoneNumber, birthDate }(#192).
    return "{\"phoneNumber\":\"" + phone + "\",\"birthDate\":\"" + BIRTH_DATE + "\"}";
  }
}
