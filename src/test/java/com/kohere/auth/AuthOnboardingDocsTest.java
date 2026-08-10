package com.kohere.auth;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails;
import static com.kohere.docs.ApiDocsErrors.assertError;
import static com.kohere.docs.ApiDocsErrors.errorSnippet;
import static com.kohere.docs.AuthDocsFields.EMAIL_CODE_400;
import static com.kohere.docs.AuthDocsFields.EMAIL_CODE_401;
import static com.kohere.docs.AuthDocsFields.EMAIL_CODE_403;
import static com.kohere.docs.AuthDocsFields.EMAIL_CODE_429;
import static com.kohere.docs.AuthDocsFields.EMAIL_CODE_502;
import static com.kohere.docs.AuthDocsFields.EMAIL_CODE_DESCRIPTION;
import static com.kohere.docs.AuthDocsFields.EMAIL_CODE_SUMMARY;
import static com.kohere.docs.AuthDocsFields.EMAIL_VERIFY_400;
import static com.kohere.docs.AuthDocsFields.EMAIL_VERIFY_401;
import static com.kohere.docs.AuthDocsFields.EMAIL_VERIFY_403;
import static com.kohere.docs.AuthDocsFields.EMAIL_VERIFY_422;
import static com.kohere.docs.AuthDocsFields.EMAIL_VERIFY_429;
import static com.kohere.docs.AuthDocsFields.EMAIL_VERIFY_DESCRIPTION;
import static com.kohere.docs.AuthDocsFields.EMAIL_VERIFY_SUMMARY;
import static com.kohere.docs.AuthDocsFields.LOGOUT_400;
import static com.kohere.docs.AuthDocsFields.LOGOUT_401;
import static com.kohere.docs.AuthDocsFields.LOGOUT_403;
import static com.kohere.docs.AuthDocsFields.LOGOUT_DESCRIPTION;
import static com.kohere.docs.AuthDocsFields.LOGOUT_SUMMARY;
import static com.kohere.docs.AuthDocsFields.ONBOARDING_400;
import static com.kohere.docs.AuthDocsFields.ONBOARDING_401;
import static com.kohere.docs.AuthDocsFields.ONBOARDING_409;
import static com.kohere.docs.AuthDocsFields.ONBOARDING_422;
import static com.kohere.docs.AuthDocsFields.ONBOARDING_DESCRIPTION;
import static com.kohere.docs.AuthDocsFields.ONBOARDING_SUMMARY;
import static com.kohere.docs.AuthDocsFields.REISSUE_400;
import static com.kohere.docs.AuthDocsFields.REISSUE_401;
import static com.kohere.docs.AuthDocsFields.REISSUE_DESCRIPTION;
import static com.kohere.docs.AuthDocsFields.REISSUE_SUMMARY;
import static com.kohere.docs.AuthDocsFields.SOCIAL_LOGIN_400;
import static com.kohere.docs.AuthDocsFields.SOCIAL_LOGIN_401;
import static com.kohere.docs.AuthDocsFields.SOCIAL_LOGIN_422;
import static com.kohere.docs.AuthDocsFields.SOCIAL_LOGIN_DESCRIPTION;
import static com.kohere.docs.AuthDocsFields.SOCIAL_LOGIN_SUMMARY;
import static com.kohere.docs.AuthDocsFields.TERMS_400;
import static com.kohere.docs.AuthDocsFields.TERMS_401;
import static com.kohere.docs.AuthDocsFields.TERMS_409;
import static com.kohere.docs.AuthDocsFields.TERMS_422;
import static com.kohere.docs.AuthDocsFields.TERMS_DESCRIPTION;
import static com.kohere.docs.AuthDocsFields.TERMS_SUMMARY;
import static com.kohere.docs.AuthDocsFields.emailCodeRequestFields;
import static com.kohere.docs.AuthDocsFields.emailCodeResponseFields;
import static com.kohere.docs.AuthDocsFields.emailVerifyRequestFields;
import static com.kohere.docs.AuthDocsFields.emailVerifyResponseFields;
import static com.kohere.docs.AuthDocsFields.onboardingRequestFields;
import static com.kohere.docs.AuthDocsFields.refreshTokenRequestField;
import static com.kohere.docs.AuthDocsFields.reissueResponseFields;
import static com.kohere.docs.AuthDocsFields.socialLoginRequestFields;
import static com.kohere.docs.AuthDocsFields.socialLoginResponseFields;
import static com.kohere.docs.AuthDocsFields.termsRequestFields;
import static com.kohere.docs.AuthDocsFields.termsResponseFields;
import static com.kohere.docs.DocsTokens.bearer;
import static com.kohere.docs.DocsTokens.expiredAccessToken;
import static com.kohere.docs.UserDocsFields.ME_401;
import static com.kohere.docs.UserDocsFields.ME_403;
import static com.kohere.docs.UserDocsFields.ME_404;
import static com.kohere.docs.UserDocsFields.ME_DESCRIPTION;
import static com.kohere.docs.UserDocsFields.ME_SUMMARY;
import static com.kohere.docs.UserDocsFields.PATCH_ME_400;
import static com.kohere.docs.UserDocsFields.PATCH_ME_401;
import static com.kohere.docs.UserDocsFields.PATCH_ME_403;
import static com.kohere.docs.UserDocsFields.PATCH_ME_404;
import static com.kohere.docs.UserDocsFields.PATCH_ME_DESCRIPTION;
import static com.kohere.docs.UserDocsFields.PATCH_ME_SUMMARY;
import static com.kohere.docs.UserDocsFields.WITHDRAW_401;
import static com.kohere.docs.UserDocsFields.WITHDRAW_404;
import static com.kohere.docs.UserDocsFields.WITHDRAW_409;
import static com.kohere.docs.UserDocsFields.WITHDRAW_DESCRIPTION;
import static com.kohere.docs.UserDocsFields.WITHDRAW_SUMMARY;
import static com.kohere.docs.UserDocsFields.meResponseFields;
import static com.kohere.docs.UserDocsFields.onboardingResponseFields;
import static com.kohere.docs.UserDocsFields.patchRequestFields;
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
import com.kohere.auth.domain.VerificationEmailSender;
import com.kohere.common.security.JwtProperties;
import com.kohere.common.security.JwtTokenService;
import com.kohere.docs.ApiDocsTags;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
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
 * 항목명이다). 문구·에러코드 배열·필드 기술자는 태그 단위 클래스가 보관한다 — Auth 태그는 {@code com.kohere.docs.AuthDocsFields},
 * Users 태그({@code GET·PATCH·DELETE /users/me})는 {@code com.kohere.docs.UserDocsFields}이며, 후자는
 * {@code LandlordOnboardingDocsTest}와 스니펫을 공유한다.
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

  // 오퍼레이션 문구·에러코드 상수(규약 1·3·4·11)와 요청/응답 필드 기술자는 태그 단위 클래스에 모여 있다 —
  // Auth 태그는 com.kohere.docs.AuthDocsFields, Users 태그(GET·PATCH·DELETE /users/me)는
  // com.kohere.docs.UserDocsFields다. 같은 오퍼레이션을 다른 파일(LandlordOnboardingDocsTest)도 문서화하므로
  // 문구·기술자를 1벌만 두고 양쪽이 같은 상수·같은 메서드를 부른다.

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
