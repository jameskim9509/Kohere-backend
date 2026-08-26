package com.kohere.auth;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails;
import static com.kohere.docs.ApiDocsErrors.errorSnippet;
import static com.kohere.docs.AuthDocsFields.SIGNUP_EMAIL_CODE_400;
import static com.kohere.docs.AuthDocsFields.SIGNUP_EMAIL_CODE_409;
import static com.kohere.docs.AuthDocsFields.SIGNUP_EMAIL_CODE_429;
import static com.kohere.docs.AuthDocsFields.SIGNUP_EMAIL_CODE_502;
import static com.kohere.docs.AuthDocsFields.SIGNUP_EMAIL_CODE_DESCRIPTION;
import static com.kohere.docs.AuthDocsFields.SIGNUP_EMAIL_CODE_SUMMARY;
import static com.kohere.docs.AuthDocsFields.SIGNUP_EMAIL_VERIFY_400;
import static com.kohere.docs.AuthDocsFields.SIGNUP_EMAIL_VERIFY_422;
import static com.kohere.docs.AuthDocsFields.SIGNUP_EMAIL_VERIFY_DESCRIPTION;
import static com.kohere.docs.AuthDocsFields.SIGNUP_EMAIL_VERIFY_SUMMARY;
import static com.kohere.docs.AuthDocsFields.signupEmailCodeRequestFields;
import static com.kohere.docs.AuthDocsFields.signupEmailCodeResponseFields;
import static com.kohere.docs.AuthDocsFields.signupEmailVerifyRequestFields;
import static com.kohere.docs.AuthDocsFields.signupEmailVerifyResponseFields;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kohere.TestcontainersConfiguration;
import com.kohere.auth.domain.EmailDispatchException;
import com.kohere.auth.domain.VerificationEmailSender;
import com.kohere.auth.domain.VerificationSmsSender;
import com.kohere.docs.ApiDocsTags;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
 * 가입용 이메일 인증(US-1-18) Spring REST Docs 스니펫 생성 테스트 — 발송·확인의 성공 응답과 스펙(01-auth-onboarding.md
 * §1-11·§1-12)의 에러 응답을 {@code build/generated-snippets}에 생성한다. <b>이 테스트가 곧 Swagger 노출 경로다</b> — 이
 * 저장소에는 springdoc이 없고 {@code openapi3.yaml}이 REST Docs 스니펫에서만 만들어지므로, 여기 없는 엔드포인트는 <b>문서에 존재하지 않는데
 * 빌드는 초록</b>이다(ADR-0017).
 *
 * <p>외부 발송 포트만 모킹한다 — {@link VerificationEmailSender}로 인증번호를 가로채 확인 단계로 잇는다. 인증번호는 Redis에 해시로만 남아
 * 되읽을 수 없으므로 <b>발송이 원문을 볼 수 있는 유일한 자리</b>이고, 그래서 이 모킹이 흉내가 아니라 실제 사용자 경로의 재현이다. {@link
 * VerificationSmsSender}는 이 흐름이 쓰지 않지만 컨텍스트 충족용으로 함께 대체해 실제 발송을 막는다. Security·JPA·Redis는 실제 구동한다.
 *
 * <p><b>이메일은 이 파일 안에서 전역 유일해야 한다</b> — 재발송 쿨다운(60초)과 시간당 한도가 Redis에 남고 같은 컨테이너를 다른 테스트와 공유하기 때문이다.
 * 발송에는 전용 IP를 실어 IP 축 예산도 갈라 둔다.
 *
 * <p><b>문서 규약(#151)</b> — 오퍼레이션(path+method)당 summary/description 상수를 1벌만 두고 성공·에러 스니펫이 같은 문자열·같은
 * 태그를 쓴다. 케이스 구분은 summary가 아니라 identifier로 한다.
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WebSignupEmailVerificationDocsTest {

  private static final String MALFORMED_BODY = "{ \"oops\" }";

  /** 성공 예시 전용 주소. */
  private static final String DOCS_EMAIL = "docs-signup-email@work.example";

  /** 재발송 쿨다운 429 예시 전용 — 60초 안에 두 번 보낸다. */
  private static final String RESEND_EMAIL = "err-signup-email-resend@work.example";

  /** 발송 실패 502 예시 전용. */
  private static final String DISPATCH_FAIL_EMAIL = "err-signup-email-dispatch@work.example";

  /** 인증번호를 한 번도 발송하지 않는 주소 — 확인 422(챌린지 부재) 예시에 쓴다. */
  private static final String UNSENT_EMAIL = "err-signup-email-unsent@work.example";

  /**
   * 발송 전용 IP. 기본 remote address(127.0.0.1)에 몰면 같은 컨테이너를 쓰는 다른 테스트가 IP 한도(20회/시간)를 나눠 쓰게 되어, 관계없는
   * 단정이 429로 깨진다.
   */
  private static final String DOCS_IP = "203.0.113.90";

  @Autowired private WebApplicationContext context;
  @MockitoBean private VerificationEmailSender emailSender;

  /** 이 흐름은 SMS를 쓰지 않지만, 목으로 대체해 두지 않으면 컨텍스트의 실제 어댑터가 그대로 남는다. */
  @MockitoBean private VerificationSmsSender smsSender;

  private final Map<String, String> sentCodes = new ConcurrentHashMap<>();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp(RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .apply(documentationConfiguration(restDocumentation))
            .build();
    reset(emailSender);
    doAnswer(
            inv -> {
              sentCodes.put(inv.getArgument(0), inv.getArgument(1));
              return null;
            })
        .when(emailSender)
        .send(any(), any());
  }

  @Test
  @DisplayName("가입용 이메일 인증 발송·확인 성공 스니펫을 생성한다")
  void generatesSignupEmailVerificationSnippets() throws Exception {
    mockMvc
        .perform(codeRequest(DOCS_EMAIL))
        .andExpect(status().isOk())
        // 마스킹은 응답 계약이다 — 평문으로 돌려주도록 바뀌어도 이 단정이 없으면 아무 테스트도 깨지지 않는다.
        .andExpect(jsonPath("$.data.email").value("do***@work.example"))
        .andExpect(jsonPath("$.data.expiresIn").value(300))
        .andDo(
            document(
                "auth-email-signup-verification-code",
                resourceDetails()
                    .tag(ApiDocsTags.AUTH)
                    .summary(SIGNUP_EMAIL_CODE_SUMMARY)
                    .description(SIGNUP_EMAIL_CODE_DESCRIPTION),
                requestFields(signupEmailCodeRequestFields()),
                responseFields(signupEmailCodeResponseFields())));

    mockMvc
        .perform(verifyRequest(DOCS_EMAIL, sentCodes.get(DOCS_EMAIL)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.verified").value(true))
        .andExpect(jsonPath("$.data.email").value("do***@work.example"))
        .andDo(
            document(
                "auth-email-signup-verify",
                resourceDetails()
                    .tag(ApiDocsTags.AUTH)
                    .summary(SIGNUP_EMAIL_VERIFY_SUMMARY)
                    .description(SIGNUP_EMAIL_VERIFY_DESCRIPTION),
                requestFields(signupEmailVerifyRequestFields()),
                responseFields(signupEmailVerifyResponseFields())));
  }

  @Test
  @DisplayName("가입용 이메일 인증 에러 스니펫을 생성한다")
  void generatesSignupEmailVerificationErrorSnippets() throws Exception {
    // ===== email/signup/verification-code =====
    perform(
        codeRequest("not-an-email"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-email-signup-verification-code-invalid-input",
        SIGNUP_EMAIL_CODE_SUMMARY,
        SIGNUP_EMAIL_CODE_DESCRIPTION,
        SIGNUP_EMAIL_CODE_400);

    perform(
        post("/api/v1/auth/email/signup/verification-code")
            .header("X-Forwarded-For", DOCS_IP)
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-email-signup-verification-code-malformed",
        SIGNUP_EMAIL_CODE_SUMMARY,
        SIGNUP_EMAIL_CODE_DESCRIPTION,
        SIGNUP_EMAIL_CODE_400);

    // 재발송 쿨다운 60초 미달 → 429. 첫 발송은 성공해야 챌린지가 생겨 두 번째가 걸린다.
    mockMvc.perform(codeRequest(RESEND_EMAIL)).andExpect(status().isOk());
    perform(
        codeRequest(RESEND_EMAIL),
        status().isTooManyRequests(),
        "TOO_MANY_REQUESTS",
        "auth-email-signup-verification-code-rate-limited",
        SIGNUP_EMAIL_CODE_SUMMARY,
        SIGNUP_EMAIL_CODE_DESCRIPTION,
        SIGNUP_EMAIL_CODE_429);

    // 발송 실패 → 502. 챌린지를 저장하지 않으므로 사용자는 그대로 재시도하면 된다.
    doThrow(new EmailDispatchException(new RuntimeException("smtp down")))
        .when(emailSender)
        .send(any(), any());
    perform(
        codeRequest(DISPATCH_FAIL_EMAIL),
        status().isBadGateway(),
        "UPSTREAM_ERROR",
        "auth-email-signup-verification-code-dispatch-failed",
        SIGNUP_EMAIL_CODE_SUMMARY,
        SIGNUP_EMAIL_CODE_DESCRIPTION,
        SIGNUP_EMAIL_CODE_502);

    // ===== email/signup/verify =====
    perform(
        post("/api/v1/auth/email/signup/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + UNSENT_EMAIL + "\",\"code\":\"\"}"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "auth-email-signup-verify-invalid-input",
        SIGNUP_EMAIL_VERIFY_SUMMARY,
        SIGNUP_EMAIL_VERIFY_DESCRIPTION,
        SIGNUP_EMAIL_VERIFY_400);

    perform(
        post("/api/v1/auth/email/signup/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "auth-email-signup-verify-malformed",
        SIGNUP_EMAIL_VERIFY_SUMMARY,
        SIGNUP_EMAIL_VERIFY_DESCRIPTION,
        SIGNUP_EMAIL_VERIFY_400);

    // 챌린지 부재(한 번도 발송하지 않은 주소) → 422. 올릴 attempts 레코드가 없어 즉시 거절이며,
    // 불일치·만료·시도 초과와 같은 코드다(비로그인 경로라 상태를 응답으로 구분해 주지 않는다).
    perform(
        verifyRequest(UNSENT_EMAIL, "000000"),
        status().isUnprocessableEntity(),
        "AUTH_EMAIL_VERIFICATION_FAILED",
        "auth-email-signup-verify-failed",
        SIGNUP_EMAIL_VERIFY_SUMMARY,
        SIGNUP_EMAIL_VERIFY_DESCRIPTION,
        SIGNUP_EMAIL_VERIFY_422);
  }

  @Test
  @DisplayName("이미 가입된 로그인 ID면 메일을 보내지 않고 409로 끊는다")
  void rejectsAlreadyRegisteredEmailWithoutSending() throws Exception {
    // 이 계정을 만드는 것 자체가 선행이다 — 가입에는 연락처·이메일 인증이 모두 필요하므로 둘 다 완주한다.
    String taken = "err-signup-email-taken@work.example";
    // 번호는 저장소 전체에서 유일해야 한다 — users.phone_number UNIQUE 는 같은 컨테이너를 쓰는 모든
    // 테스트 클래스가 공유하므로, 겹치면 이 가입이 409 RESOURCE_CONFLICT 로 끝난다(실제로 한 번 겹쳤다).
    String phone = "01055559301";
    doAnswer(
            inv -> {
              sentCodes.put(inv.getArgument(0), inv.getArgument(1));
              return null;
            })
        .when(smsSender)
        .send(any(), any());
    mockMvc
        .perform(
            post("/api/v1/auth/phone/signup/verification-code")
                .header("X-Forwarded-For", DOCS_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + phone + "\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/auth/phone/signup/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"phoneNumber\":\""
                        + phone
                        + "\",\"code\":\""
                        + sentCodes.get(phone)
                        + "\"}"))
        .andExpect(status().isOk());
    mockMvc.perform(codeRequest(taken)).andExpect(status().isOk());
    mockMvc.perform(verifyRequest(taken, sentCodes.get(taken))).andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "김임대",
                      "birthDate": "1990-01-01",
                      "phoneNumber": "%s",
                      "email": "%s",
                      "password": "Kohere1!",
                      "termsOfServiceAgreed": true,
                      "privacyPolicyAgreed": true,
                      "marketingAgreed": false
                    }
                    """
                        .formatted(phone, taken)))
        .andExpect(status().isOk());

    reset(emailSender);

    // 이제 같은 주소로 인증번호를 요청하면 409다. **메일이 나가지 않는 것까지가 이 케이스의 요지다** —
    // 감추고 발송하면 남의 메일함으로 인증번호가 날아가고, 그건 이메일 채널에서 발송 자체가 피해다.
    perform(
        codeRequest(taken),
        status().isConflict(),
        "AUTH_EMAIL_ALREADY_REGISTERED",
        "auth-email-signup-verification-code-already-registered",
        SIGNUP_EMAIL_CODE_SUMMARY,
        SIGNUP_EMAIL_CODE_DESCRIPTION,
        SIGNUP_EMAIL_CODE_409);
    verify(emailSender, never()).send(any(), any());
  }

  /** 발송에는 전용 IP를 실어 다른 테스트와 IP 축 예산을 나눠 쓰지 않게 한다. */
  private MockHttpServletRequestBuilder codeRequest(String email) {
    return post("/api/v1/auth/email/signup/verification-code")
        .header("X-Forwarded-For", DOCS_IP)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\"}");
  }

  private MockHttpServletRequestBuilder verifyRequest(String email, String code) {
    return post("/api/v1/auth/email/signup/verify")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}");
  }

  private void perform(
      MockHttpServletRequestBuilder request,
      ResultMatcher expectedStatus,
      String expectedCode,
      String identifier,
      String summary,
      String description,
      String... errorCodes)
      throws Exception {
    mockMvc
        .perform(request)
        .andExpect(expectedStatus)
        .andExpect(jsonPath("$.error.code").value(expectedCode))
        .andDo(errorSnippet(identifier, ApiDocsTags.AUTH, summary, description, errorCodes));
  }
}
