package com.kohere.gamification.infrastructure;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails;
import static com.kohere.docs.ApiDocsErrors.assertError;
import static com.kohere.docs.ApiDocsErrors.errorSnippet;
import static com.kohere.docs.DocsTokens.bearer;
import static com.kohere.docs.DocsTokens.expiredAccessToken;
import static com.kohere.docs.QuizDocsFields.QUIZ_ANSWER_400;
import static com.kohere.docs.QuizDocsFields.QUIZ_ANSWER_401;
import static com.kohere.docs.QuizDocsFields.QUIZ_ANSWER_404;
import static com.kohere.docs.QuizDocsFields.QUIZ_ANSWER_DESCRIPTION;
import static com.kohere.docs.QuizDocsFields.QUIZ_ANSWER_SUMMARY;
import static com.kohere.docs.QuizDocsFields.QUIZ_RANDOM_401;
import static com.kohere.docs.QuizDocsFields.QUIZ_RANDOM_404;
import static com.kohere.docs.QuizDocsFields.QUIZ_RANDOM_DESCRIPTION;
import static com.kohere.docs.QuizDocsFields.QUIZ_RANDOM_SUMMARY;
import static com.kohere.docs.QuizDocsFields.answerPathParameters;
import static com.kohere.docs.QuizDocsFields.answerRequestFields;
import static com.kohere.docs.QuizDocsFields.answerResponseFields;
import static com.kohere.docs.QuizDocsFields.randomResponseFields;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kohere.TestcontainersConfiguration;
import com.kohere.common.security.JwtProperties;
import com.kohere.common.security.JwtTokenService;
import com.kohere.docs.ApiDocsTags;
import com.kohere.gamification.infrastructure.QuizDocument.ChoiceSpec;
import com.kohere.user.api.UserAccountService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
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
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Spring REST Docs 스니펫 생성 테스트(ADR-0007·0016). 학습 퀴즈 엔드포인트(랜덤 조회·정답 채점)의 성공 응답과 스펙
 * (06-gamification.md)에 정의된 에러 응답을 {@code build/generated-snippets}에 생성해 OpenAPI3(Swagger UI)에
 * 합류한다.
 *
 * <p>cross-module 협력(user 표시 언어)은 {@code @MockitoBean}으로 대체하고 access 토큰은 {@link JwtTokenService}로
 * 직접 발급한다. MongoDB(퀴즈 카탈로그)는 실제 컨테이너로 구동하며, 시더는 {@code test} 프로파일에서 비활성이라 이 테스트가 직접 시드한다.
 *
 * <p><b>#181로 인가 계약이 바뀌었다</b> — 퀴즈는 {@code permitAll}이라 토큰 없이도 2xx이고 세입자 게이트가 없다. 그래서 401
 * UNAUTHENTICATED·403 AUTH_ONBOARDING_REQUIRED·403 FORBIDDEN 스니펫은 도달 불가라 제거했고, 그 자리를 {@link
 * #guestAccessWithoutToken}·{@link #forgedTokenIsTreatedAsGuest}·{@link #landlordGetsKoreanLabels}가
 * 2xx 계약으로 고정한다. 401은 <b>만료 토큰</b>에만 남는다.
 *
 * <p>문서 문구는 오퍼레이션(path+method)당 상수 1벌({@code QUIZ_RANDOM_*}·{@code QUIZ_ANSWER_*})을 성공·에러 스니펫이
 * 공유한다(#151) — 생성기가 같은 오퍼레이션의 summary/description 중 첫 non-blank 하나만 채택하기 때문이다. 그 상수와 필드 기술자는 태그 단위
 * 클래스 {@link com.kohere.docs.QuizDocsFields}에 모아 두고 여기서는 static import로 부른다 — 이 파일에는 테스트 흐름만 남긴다.
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
@Testcontainers
@Import(TestcontainersConfiguration.class)
class QuizDocsTest {

  @Container @ServiceConnection static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

  private static final String MALFORMED_BODY = "{ \"oops\" }";

  // 서명이 깨진 access 토큰. #181 이후 위조 토큰은 401이 아니라 "신원 없는 요청"과 같은 게스트 취급이라 200이다
  // (forgedTokenIsTreatedAsGuest가 그 계약을 고정한다). restdocs-api-spec 이 무인증 예시에서도
  // bearerAuthJWT 스킴을 도출하게 한다.
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

  @Autowired private WebApplicationContext context;
  @Autowired private JwtTokenService jwtTokenService;
  @Autowired private JwtProperties jwtProperties;
  @Autowired private QuizMongoRepository quizMongoRepository;

  @MockitoBean private UserAccountService userAccountService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp(RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .apply(documentationConfiguration(restDocumentation))
            .build();
    quizMongoRepository.deleteAll();
    // 표시 언어는 user 공개 query로 취득(ADR-0029) — 사용자가 고른 ko(users.lang)를 가정한다.
    // userType은 더 이상 조회하지 않는다(#181로 세입자 게이트 제거) — 스텁을 두면 죽은 코드다.
    given(userAccountService.getLanguage(anyLong())).willReturn("ko");
  }

  @Test
  void generatesQuizSnippets() throws Exception {
    seedQuiz();
    String token = jwtTokenService.issueAccessToken(1L);

    // ① 랜덤 퀴즈 조회 — 표시 언어로 번역, 정답·해설 미포함
    mockMvc
        .perform(get("/api/v1/quizzes/random").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.quizId").value(4001))
        .andExpect(jsonPath("$.data.choices.length()").value(4))
        .andDo(
            document(
                "quiz-get-random",
                resourceDetails()
                    .tag(ApiDocsTags.QUIZ)
                    .summary(QUIZ_RANDOM_SUMMARY)
                    .description(QUIZ_RANDOM_DESCRIPTION),
                responseFields(randomResponseFields())));

    // ② 정답 제출 — 정답: correct=true + 번역된 해설(정답 키는 미노출)
    //    correctChoice가 optional로 문서화되므로(정답/오답 200 스키마 통합) 「키 미노출」 계약은
    //    아래 doesNotExist 단정이 지킨다 — 이게 없으면 정답 응답의 키 유출을 아무도 못 잡는다(#151 규약 13).
    mockMvc
        .perform(
            post("/api/v1/quizzes/{quizId}/answer", 4001L)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("A")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.correct").value(true))
        .andExpect(jsonPath("$.data.correctChoice").doesNotExist())
        .andExpect(jsonPath("$.data.explanation").value("확정일자를 받으면 보증금을 보호받습니다."))
        .andDo(
            document(
                "quiz-answer-correct",
                resourceDetails()
                    .tag(ApiDocsTags.QUIZ)
                    .summary(QUIZ_ANSWER_SUMMARY)
                    .description(QUIZ_ANSWER_DESCRIPTION),
                pathParameters(answerPathParameters()),
                requestFields(answerRequestFields()),
                responseFields(answerResponseFields())));

    // ③ 정답 제출 — 오답: 정답 키(correctChoice)와 번역된 해설(explanation)
    mockMvc
        .perform(
            post("/api/v1/quizzes/{quizId}/answer", 4001L)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("B")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.correct").value(false))
        .andExpect(jsonPath("$.data.correctChoice").value("A"))
        .andDo(
            document(
                "quiz-answer-wrong",
                resourceDetails()
                    .tag(ApiDocsTags.QUIZ)
                    .summary(QUIZ_ANSWER_SUMMARY)
                    .description(QUIZ_ANSWER_DESCRIPTION),
                pathParameters(answerPathParameters()),
                requestFields(answerRequestFields()),
                responseFields(answerResponseFields())));
  }

  /** 스펙의 "발생 가능한 에러"를 엔드포인트별로 실제 트리거해 스니펫으로 생성하고 status·error.code를 단정한다. */
  @Test
  void generatesQuizErrorSnippets() throws Exception {
    String token = jwtTokenService.issueAccessToken(1L);
    String expiredToken = expiredAccessToken(jwtProperties);

    // ===== GET /quizzes/random =====
    // #181로 퀴즈가 permitAll이 되면서 401 UNAUTHENTICATED(누락·위조)·403 AUTH_ONBOARDING_REQUIRED
    // ·403 FORBIDDEN(비-세입자) 세 케이스는 도달 불가가 됐다 — 아래 guestAccessWithoutToken·
    // landlordGetsKoreanLabels가 그 자리를 2xx 계약으로 대신 고정한다. 만료만 401로 남는다.
    performRandomError(
        get("/api/v1/quizzes/random").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "quiz-get-random-token-expired",
        QUIZ_RANDOM_401);

    performRandomError(
        get("/api/v1/quizzes/random").header(HttpHeaders.AUTHORIZATION, bearer(token)),
        status().isNotFound(),
        "QUIZ_NOT_FOUND",
        "quiz-get-random-not-found",
        QUIZ_RANDOM_404);

    // ===== POST /quizzes/{quizId}/answer =====
    performAnswerError(
        post("/api/v1/quizzes/{quizId}/answer", 4001L)
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(answerJson("E")),
        status().isBadRequest(),
        "INVALID_INPUT",
        "quiz-answer-invalid-input",
        QUIZ_ANSWER_400);

    // 문서용 스니펫은 본문 없이 만든다(#151-4) — 본문이 비면 restdocs-api-spec이 requestBody 조립에서
    // 이 스니펫을 통째로 빼므로 같은 요청 예시가 중복 노출되지 않는다. 본문 누락도 깨진 JSON과 같은
    // HttpMessageNotReadableException이라 MALFORMED_REQUEST로 수렴한다.
    performAnswerError(
        post("/api/v1/quizzes/{quizId}/answer", 4001L)
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .contentType(MediaType.APPLICATION_JSON),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "quiz-answer-malformed",
        QUIZ_ANSWER_400);

    // 「깨진 JSON 거부」 계약은 문서화 없이 단정만으로 유지한다 — 위 스니펫이 검증하는 시나리오는
    // 「본문 누락」이라 이게 없으면 파싱 실패 경로의 회귀 방어가 사라진다.
    assertError(
        mockMvc,
        post("/api/v1/quizzes/{quizId}/answer", 4001L)
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST");

    // 401은 시큐리티 필터(DispatcherServlet 이전)에서 끊기므로 본문과 무관하다 — 요청 예시 중복을 없애려 본문을 뺐다.
    performAnswerError(
        post("/api/v1/quizzes/{quizId}/answer", 4001L)
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "quiz-answer-token-expired",
        QUIZ_ANSWER_401);

    performAnswerError(
        post("/api/v1/quizzes/{quizId}/answer", 9999L)
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(answerJson("A")),
        status().isNotFound(),
        "QUIZ_NOT_FOUND",
        "quiz-answer-not-found",
        QUIZ_ANSWER_404);
  }

  /**
   * 게이트 해제 계약(#181) — {@code Authorization} 헤더 <b>없이</b> 퀴즈 두 엔드포인트가 2xx다.
   *
   * <p>기본 언어가 {@code en}인 것만 보면 부족하다 — {@code getLanguage}를 부르고 예외를 삼키는 구현도 통과한다. 게스트는 {@code
   * users} 행이 없어 호출 자체가 404이므로 <b>한 번도 부르지 않음</b>을 함께 못 박는다.
   *
   * <p>이 흐름은 스니펫으로도 낸다(#151-3) — 회원 예시({@code quiz-get-random}·{@code quiz-answer-correct})는 ko
   * 라벨이라 「토큰 없이 호출하면 en으로 온다」는 계약이 Swagger에 전혀 보이지 않았다. 같은 {@code (path, method, 200)}이라 스키마는 회원
   * 예시와 병합되고(그래서 <b>같은 필드 헬퍼</b>를 쓴다) 예시만 identifier 별로 드롭다운에 나란히 남는다.
   */
  @Test
  void guestAccessWithoutToken() throws Exception {
    seedQuiz();

    mockMvc
        .perform(get("/api/v1/quizzes/random"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.quizId").value(4001))
        .andExpect(jsonPath("$.data.question").value("Which protects a jeonse tenant?"))
        .andDo(
            document(
                "quiz-get-random-guest",
                resourceDetails()
                    .tag(ApiDocsTags.QUIZ)
                    .summary(QUIZ_RANDOM_SUMMARY)
                    .description(QUIZ_RANDOM_DESCRIPTION),
                responseFields(randomResponseFields())));

    mockMvc
        .perform(
            post("/api/v1/quizzes/{quizId}/answer", 4001L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("A")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.correct").value(true))
        .andExpect(jsonPath("$.data.correctChoice").doesNotExist())
        .andExpect(jsonPath("$.data.explanation").value("A fixed date protects your deposit."))
        .andDo(
            document(
                "quiz-answer-correct-guest",
                resourceDetails()
                    .tag(ApiDocsTags.QUIZ)
                    .summary(QUIZ_ANSWER_SUMMARY)
                    .description(QUIZ_ANSWER_DESCRIPTION),
                pathParameters(answerPathParameters()),
                requestFields(answerRequestFields()),
                responseFields(answerResponseFields())));

    verify(userAccountService, never()).getLanguage(anyLong());
    verify(userAccountService, never()).getUserType(anyLong());
  }

  /** 위조·형식 오류 토큰은 (만료와 달리) 게스트로 통과한다 — 신원 없는 요청과 같은 취급이다(#181). */
  @Test
  void forgedTokenIsTreatedAsGuest() throws Exception {
    seedQuiz();

    mockMvc
        .perform(
            get("/api/v1/quizzes/random").header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.question").value("Which protects a jeonse tenant?"));

    verify(userAccountService, never()).getLanguage(anyLong());
  }

  /**
   * 임대인도 200이며 응답 언어는 본인 {@code users.lang}(ko)이다 — 세입자 게이트가 사라졌음을 HTTP 계약으로 고정한다(#181). 게이트를 되살리면
   * 여기서 403으로 깨진다.
   */
  @Test
  void landlordGetsKoreanLabels() throws Exception {
    seedQuiz();
    String landlordToken = jwtTokenService.issueAccessToken(3L);

    mockMvc
        .perform(
            get("/api/v1/quizzes/random").header(HttpHeaders.AUTHORIZATION, bearer(landlordToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.question").value("전세 임차인 보호 제도는?"));

    verify(userAccountService, never()).getUserType(anyLong());
  }

  // ---- helpers ----

  private void performRandomError(
      MockHttpServletRequestBuilder request,
      ResultMatcher expectedStatus,
      String expectedCode,
      String identifier,
      String... errorCodes)
      throws Exception {
    mockMvc
        .perform(request)
        .andExpect(expectedStatus)
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(expectedCode))
        .andDo(
            errorSnippet(
                identifier,
                ApiDocsTags.QUIZ,
                QUIZ_RANDOM_SUMMARY,
                QUIZ_RANDOM_DESCRIPTION,
                errorCodes));
  }

  /** path 변수가 있는 오퍼레이션이라 에러 스니펫에도 {@code pathParameters}를 선언한다(#151 규약 12). */
  private void performAnswerError(
      MockHttpServletRequestBuilder request,
      ResultMatcher expectedStatus,
      String expectedCode,
      String identifier,
      String... errorCodes)
      throws Exception {
    mockMvc
        .perform(request)
        .andExpect(expectedStatus)
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(expectedCode))
        .andDo(
            errorSnippet(
                identifier,
                ApiDocsTags.QUIZ,
                QUIZ_ANSWER_SUMMARY,
                QUIZ_ANSWER_DESCRIPTION,
                answerPathParameters(),
                errorCodes));
  }

  private void seedQuiz() {
    quizMongoRepository.save(
        QuizDocument.builder()
            .id(4001L)
            .active(true)
            .question(Map.of("en", "Which protects a jeonse tenant?", "ko", "전세 임차인 보호 제도는?"))
            .choices(
                List.of(
                    ChoiceSpec.builder()
                        .key("A")
                        .text(Map.of("en", "Fixed date", "ko", "확정일자"))
                        .build(),
                    ChoiceSpec.builder().key("B").text(Map.of("en", "Fee", "ko", "관리비")).build(),
                    ChoiceSpec.builder().key("C").text(Map.of("en", "Loan", "ko", "대출")).build(),
                    ChoiceSpec.builder().key("D").text(Map.of("en", "Tax", "ko", "세금")).build()))
            .correctChoice("A")
            .explanation(
                Map.of("en", "A fixed date protects your deposit.", "ko", "확정일자를 받으면 보증금을 보호받습니다."))
            .build());
  }

  private static String answerJson(String selectedChoice) {
    return "{\"selectedChoice\":\"" + selectedChoice + "\"}";
  }
}
