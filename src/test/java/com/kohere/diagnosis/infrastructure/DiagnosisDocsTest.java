package com.kohere.diagnosis.infrastructure;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails;
import static com.kohere.docs.ApiDocsErrors.assertError;
import static com.kohere.docs.ApiDocsErrors.errorSnippet;
import static com.kohere.docs.ApiDocsFields.codeField;
import static com.kohere.docs.ApiDocsFields.enumField;
import static com.kohere.docs.ApiDocsFields.errorNull;
import static com.kohere.docs.ApiDocsFields.field;
import static com.kohere.docs.ApiDocsFields.optCodeArrayField;
import static com.kohere.docs.ApiDocsFields.optCodeField;
import static com.kohere.docs.ApiDocsFields.optEnumField;
import static com.kohere.docs.ApiDocsFields.optField;
import static com.kohere.docs.DiagnosisDocsFields.ANSWER_FIELD_CODES;
import static com.kohere.docs.DiagnosisDocsFields.DETAIL_401;
import static com.kohere.docs.DiagnosisDocsFields.DETAIL_403;
import static com.kohere.docs.DiagnosisDocsFields.DETAIL_404;
import static com.kohere.docs.DiagnosisDocsFields.DETAIL_DESCRIPTION;
import static com.kohere.docs.DiagnosisDocsFields.DETAIL_SUMMARY;
import static com.kohere.docs.DiagnosisDocsFields.DIAGNOSIS_CONDITION_CODES;
import static com.kohere.docs.DiagnosisDocsFields.LANGUAGE_NOTE;
import static com.kohere.docs.DiagnosisDocsFields.QUESTION_FIELD_CODES;
import static com.kohere.docs.DiagnosisDocsFields.QUESTION_TABLE;
import static com.kohere.docs.DiagnosisDocsFields.SEED_NOTE;
import static com.kohere.docs.DiagnosisDocsFields.SELECTABLE_CONDITION_CODES;
import static com.kohere.docs.DiagnosisDocsFields.SELECT_TYPE_CODES;
import static com.kohere.docs.DiagnosisDocsFields.SUBMIT_400;
import static com.kohere.docs.DiagnosisDocsFields.SUBMIT_401;
import static com.kohere.docs.DiagnosisDocsFields.SUBMIT_DESCRIPTION;
import static com.kohere.docs.DiagnosisDocsFields.SUBMIT_SUMMARY;
import static com.kohere.docs.DiagnosisDocsFields.detailResponseFields;
import static com.kohere.docs.DiagnosisDocsFields.diagnosisIdPathParameters;
import static com.kohere.docs.DiagnosisDocsFields.diagnosisSummaryFields;
import static com.kohere.docs.DiagnosisDocsFields.historyQueryParameters;
import static com.kohere.docs.DiagnosisDocsFields.pageFields;
import static com.kohere.docs.DiagnosisDocsFields.recommendationContentFields;
import static com.kohere.docs.DiagnosisDocsFields.recommendationQueryParameters;
import static com.kohere.docs.DiagnosisDocsFields.submitResponseFields;
import static com.kohere.docs.DocsTokens.bearer;
import static com.kohere.docs.DocsTokens.expiredAccessToken;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kohere.TestcontainersConfiguration;
import com.kohere.common.response.PageInfo;
import com.kohere.common.response.PageResponse;
import com.kohere.common.security.JwtProperties;
import com.kohere.common.security.JwtTokenService;
import com.kohere.diagnosis.domain.ArcStatus;
import com.kohere.diagnosis.domain.DiagnosisStatus;
import com.kohere.diagnosis.domain.District;
import com.kohere.diagnosis.domain.Purpose;
import com.kohere.diagnosis.domain.Region;
import com.kohere.diagnosis.domain.UniversityGroup;
import com.kohere.diagnosis.infrastructure.DiagnosisQuestionDocument.OptionSpec;
import com.kohere.diagnosis.infrastructure.DiagnosisQuestionDocument.SelectSpec;
import com.kohere.diagnosis.infrastructure.DiagnosisSuggestionDocument.ActionSpec;
import com.kohere.docs.ApiDocsTags;
import com.kohere.listing.api.ListingCodeLabelView;
import com.kohere.listing.api.ListingRecommendationService;
import com.kohere.listing.api.RecommendedListingView;
import com.kohere.user.api.UserAccountService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.request.ParameterDescriptor;
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
 * Spring REST Docs 스니펫 생성 테스트(ADR-0007·0016). 진단·추천 v1 엔드포인트(단계별 질문 조회·단계 답 저장·확정·이력·최근·단건 상세·추천)의
 * 성공 응답과, 스펙(02-diagnosis-recommendation.md)에 정의된 주요 에러 응답을 {@code build/generated-snippets}에 생성한다
 * — auth-onboarding과 동일한 방식으로 OpenAPI3 명세(Swagger UI)에 합류한다.
 *
 * <p><b>문서 규약</b>(#151) — 오퍼레이션(path+method)당 summary·description 상수를 한 벌 만들고 그 오퍼레이션의 성공·에러 스니펫이
 * 전부 같은 문자열을 쓴다(생성기는 첫 non-blank 하나만 채택하고 순서는 파일 순회에 좌우된다). 태그는 {@link ApiDocsTags#DIAGNOSIS} 하나이며
 * v1/v2를 나누지 않는다 — 두 파일이 같은 오퍼레이션을 캡처할 수 있어 나누면 중복 노출된다. {@code POST /api/v1/diagnoses}와 {@code GET
 * /api/v1/diagnoses/{diagnosisId}}의 문구·필드 기술자는 {@link com.kohere.docs.DiagnosisDocsFields}가 정본이다.
 *
 * <p>cross-module 협력(listing 추천·user 표시 언어)은 {@code @MockitoBean}으로 대체하고 access 토큰은 {@link
 * JwtTokenService}로 직접 발급한다(test-strategy §4, 통합 테스트 {@link DiagnosisMongoIntegrationTest}와 동일 전략).
 * MongoDB(문항·제안 카탈로그·진단 영속)는 실제 컨테이너로, Security·JPA·Redis 컨텍스트는 실제로 구동한다. 진단 문항·제안 시더는 {@code test}
 * 프로파일에서 비활성이라 이 테스트가 직접 카탈로그를 시드한다 — 시드 선택지는 운영 카탈로그보다 짧으므로 문서에는 실제 허용 코드를 스키마 enum으로 싣는다.
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
@Testcontainers
@Import(TestcontainersConfiguration.class)
class DiagnosisDocsTest {

  @Container @ServiceConnection static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

  private static final String MALFORMED_BODY = "{ \"oops\" }";

  /** v1 조정 제안 액션 코드(MongoDB {@code diagnosisSuggestions} 카탈로그 — enum 클래스가 없다). */
  private static final List<String> SUGGESTION_ACTION_CODES =
      List.of("RELAX_REGION", "RELAX_CONDITIONS", "INCREASE_BUDGET");

  /** v1 조정 제안 사유 코드. 현재 카탈로그에는 {@code NO_MATCH} 하나뿐이다. */
  private static final List<String> SUGGESTION_REASON_CODES = List.of("NO_MATCH");

  // ---- 오퍼레이션 상수: GET /api/v1/diagnoses/questions/{step} ----

  private static final String QUESTION_SUMMARY = "단계별 진단 질문 조회";
  private static final String QUESTION_DESCRIPTION =
      """
      진단 6단계 중 지정한 단계의 질문 1개와 선택지를 조회한다.

      **인증**

      - 회원 전용이다. `Authorization: Bearer <accessToken>`가 필요하다.
      - 비회원은 서버가 순서를 정하는 v2 흐름(`POST /api/v2/diagnoses/start`)을 쓴다.

      **단계와 선택지**

      """
          + QUESTION_TABLE
          + """

      - 다음 `step` 번호는 클라이언트가 정하며 이 조회는 답을 저장하지 않는다.
      - ③(`step=3`)은 진행 중 진단에 저장된 ② `purpose`로 서버가 `university`(`STUDY`)와 `district`(`NON_STUDY`) 중 하나만 고른다. 클라이언트가 분기하지 않으며, `purpose`를 저장하기 전에 호출하면 400이다.
      - 응답의 `data.field`를 `POST /api/v1/diagnoses/answers` 요청의 `field`에 그대로 싣는다.
      - `regionRetry`는 v2 흐름 전용 예외질문이라 이 엔드포인트로는 내려오지 않는다.
      - """
          + LANGUAGE_NOTE
          + """

      - """
          + SEED_NOTE
          + """


      **에러 코드**

      - `400 INVALID_INPUT` — `step`이 1~6 밖이거나, ③ 조회인데 ② `purpose`가 선행되지 않음
      - `401 UNAUTHENTICATED` — 토큰 없음 또는 위조
      - `401 TOKEN_EXPIRED` — 액세스 토큰 만료
      """;
  private static final String[] QUESTION_400 = {"INVALID_INPUT"};
  private static final String[] QUESTION_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};

  // ---- 오퍼레이션 상수: POST /api/v1/diagnoses/answers ----

  private static final String ANSWER_SUMMARY = "단계 답 저장";
  private static final String ANSWER_DESCRIPTION =
      """
      현재 단계의 답 1개를 진행 중(IN_PROGRESS) 진단에 저장한다.

      **인증**

      - 회원 전용이다. 진행 중 진단은 토큰의 사용자로 식별하며 사용자당 1건이다.
      - 진행 중 진단이 없으면 첫 답을 저장할 때 서버가 만든다.

      **답의 세 가지 형태**

      | 단계 | `select.type` | 본문 |
      | --- | --- | --- |
      | ①②③⑥ 단일 선택 | `SINGLE` | `{ "field": "...", "code": "..." }` |
      | ④ 주거 조건 | `MULTI` | `{ "field": "conditions", "codes": ["...", "..."] }`(최대 3개·중복 불가) |
      | ⑤ 월세 범위 | `NUMBER_RANGE` | `{ "field": "monthlyRent", "min": 300000, "max": 600000 }` |

      - 해당하는 쪽만 채우고 나머지 필드는 보내지 않는다. 누적 답을 묶어 재전송하지 않는다.
      - `field`는 직전 질문 응답의 `data.field`를 그대로 싣는다.
      - `min`·`max`는 KRW 정수이며 각 0 이상이고 `min <= max`여야 한다.
      - 파생 조건 `NO_ARC`는 ⑥ `arcStatus` 답에서 서버가 만들어 붙이는 값이라 `codes`로 직접 고를 수 없다.
      - 저장된 답은 확정(`POST /api/v1/diagnoses`) 시점에 다시 검증된다.

      **에러 코드**

      - `400 INVALID_INPUT` — 미정의 코드, 현재 단계와 맞지 않는 `field`, 목적과 대학/지역 불일치, `conditions` 4개 이상, 월세 범위 위반
      - `400 MALFORMED_REQUEST` — 본문 JSON 해석 불가(검증 이전)
      - `401 UNAUTHENTICATED` — 토큰 없음 또는 위조
      - `401 TOKEN_EXPIRED` — 액세스 토큰 만료
      """;
  private static final String[] ANSWER_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  private static final String[] ANSWER_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};

  // ---- 오퍼레이션 상수: GET /api/v1/diagnoses ----

  private static final String HISTORY_SUMMARY = "내 진단 이력 목록";
  private static final String HISTORY_DESCRIPTION =
      """
      내가 확정한 진단 이력을 오프셋 페이지네이션으로 조회한다.

      **인증**

      - 회원 전용이며 토큰의 사용자 본인 이력만 조회된다.
      - 게스트가 v2로 만든 진단은 이 목록의 대상이 아니다.

      **응답 규칙**

      - 확정(`COMPLETED`) 진단만 나온다. 진행 중(`IN_PROGRESS`)과 v2 폐기 기록(`DISCARDED`)은 빠진다.
      - 기본 정렬은 `submittedAt,desc`이고 허용 키는 `submittedAt` 하나다.
      - `content[]` 항목은 진단 단건 상세와 같은 입력 요약이며 `status`·`submittedAt`이 더 붙는다.
      - ② `purpose`에 따라 `university`(`STUDY`)와 `district`(`NON_STUDY`) 중 한쪽만 채워지고 반대쪽은 `null`이다.
      - 이력이 없으면 `content=[]`이며 에러가 아니다.

      **에러 코드**

      - `400 INVALID_INPUT` — `page`/`size` 범위 위반, 허용되지 않은 `sort` 키 또는 방향
      - `401 UNAUTHENTICATED` — 토큰 없음 또는 위조
      - `401 TOKEN_EXPIRED` — 액세스 토큰 만료
      """;
  private static final String[] HISTORY_400 = {"INVALID_INPUT"};
  private static final String[] HISTORY_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};

  // ---- 오퍼레이션 상수: GET /api/v1/diagnoses/latest ----

  private static final String LATEST_SUMMARY = "최근 진단 단건";
  private static final String LATEST_DESCRIPTION =
      """
      홈 화면의 "진단 시작 / 재진단" 분기를 위해 가장 최근 확정 진단 1건을 조회한다.

      **인증**

      - 회원 전용이며 토큰의 사용자 본인 진단만 조회된다.

      **응답 규칙**

      - 확정 이력이 없어도 404가 아니라 200이고 `data.completed=false`다. 클라이언트는 이 한 필드로 분기한다.
      - `completed=false`일 때 나머지 요약 필드는 **키가 사라지지 않고 값이 `null`로 실린다**(`diagnosisId`·`region`·`purpose`·`university`·`district`·`conditions`·`monthlyRentMin`·`monthlyRentMax`·`arcStatus`·`submittedAt`).
      - `completed=true`일 때도 ② `purpose`에 따라 `university`(`STUDY`)와 `district`(`NON_STUDY`) 중 한쪽은 `null`이다.
      - 진행 중(`IN_PROGRESS`) 진단은 대상이 아니다 — 확정된 것만 본다.

      **에러 코드**

      - `401 UNAUTHENTICATED` — 토큰 없음 또는 위조
      - `401 TOKEN_EXPIRED` — 액세스 토큰 만료
      """;
  private static final String[] LATEST_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};

  // ---- 오퍼레이션 상수: GET /api/v1/diagnoses/{diagnosisId}/recommendations ----

  private static final String RECOMMENDATIONS_SUMMARY = "진단 결과 추천 매물";
  private static final String RECOMMENDATIONS_DESCRIPTION =
      """
      확정된 진단 조건에 맞는 매물 카드와 지도 마커를 함께 조회한다.

      **인증**

      - 회원 전용이며 본인 소유 진단만 조회된다. 타인 소유는 `403 FORBIDDEN`이다.
      - 게스트의 추천 조회는 v2-3(`GET /api/v2/diagnoses/{diagnosisId}/recommendations`)이 담당한다.

      **화면 구성**

      - 매물 카드는 `content[]`로, 지도 마커는 `markers[]`로 그리고 둘은 같은 `listingId`로 연결한다.
      - 매물 유형과 조건 배지는 `type.label`·`conditions[].label`을 표시하고, 필터 재요청이나 내부 비교에는 같은 객체의 `code`를 쓴다.
      - `title`과 `label`은 사용자 표시 언어로 선택되어 온다.
      - 정렬 허용 키는 `recommended`·`price`·`distance`이며 기본은 `recommended,desc`다.

      **추천이 0건일 때**

      - `content=[]`·`markers=[]`는 정상 응답이며 에러가 아니다.
      - 이때만 `suggestions`가 채워진다 — `reason`·`actions[].type`은 언어 무관 코드이고 `message`·`actions[].detail`은 사용자 언어 문구다.
      - 결과가 있으면 `suggestions`는 `null`이다(키는 남는다). v2-3에는 이 필드 자체가 없다.

      **에러 코드**

      - `400 INVALID_INPUT` — `page`/`size` 범위 위반, 허용되지 않은 `sort` 키 또는 방향
      - `401 UNAUTHENTICATED` — 토큰 없음 또는 위조
      - `401 TOKEN_EXPIRED` — 액세스 토큰 만료
      - `403 FORBIDDEN` — 타인 소유 진단 접근
      - `404 DIAGNOSIS_NOT_FOUND` — 진단이 존재하지 않거나 폐기 기록
      """;
  private static final String[] RECOMMENDATIONS_400 = {"INVALID_INPUT"};
  private static final String[] RECOMMENDATIONS_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  private static final String[] RECOMMENDATIONS_403 = {"FORBIDDEN"};
  private static final String[] RECOMMENDATIONS_404 = {"DIAGNOSIS_NOT_FOUND"};

  // 서명이 깨진(다른 키로 서명) access 토큰. 서버 검증에서 401 UNAUTHENTICATED 를 유발하면서도 구조상 JWT 라,
  // restdocs-api-spec 이 무인증 예시에서도 bearerAuthJWT 보안 스킴을 도출하게 한다(모든 예시가 Bearer JWT 헤더를
  // 갖게 해 비결정적 스니펫 병합 순서와 무관하게 Swagger 자물쇠가 유지된다 — auth 문서화와 동일 처리).
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
  @Autowired private DiagnosisMongoRepository diagnosisMongoRepository;
  @Autowired private DiagnosisQuestionMongoRepository questionMongoRepository;
  @Autowired private DiagnosisSuggestionMongoRepository suggestionMongoRepository;

  @MockitoBean private UserAccountService userAccountService;
  @MockitoBean private ListingRecommendationService listingRecommendationService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp(RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .apply(documentationConfiguration(restDocumentation))
            .build();
    diagnosisMongoRepository.deleteAll();
    questionMongoRepository.deleteAll();
    suggestionMongoRepository.deleteAll();
    seedQuestions();
    seedSuggestion();
    // 표시 언어는 user 공개 query로 취득(ADR-0029) — users.lang='ko'인 사용자를 가정해 한국어 라벨을 내려받는다.
    given(userAccountService.getLanguage(anyLong())).willReturn("ko");
    given(listingRecommendationService.recommendByCriteria(any(), anyString()))
        .willAnswer(
            invocation ->
                listingRecommendationService.recommendByCriteria(invocation.getArgument(0)));
  }

  @Test
  void generatesDiagnosisSnippets() throws Exception {
    long userId = 1L;
    String token = jwtTokenService.issueAccessToken(userId);

    // ① 단계별 질문 조회(step 1 = region)
    mockMvc
        .perform(
            get("/api/v1/diagnoses/questions/{step}", 1)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.step").value(1))
        .andExpect(jsonPath("$.data.field").value("region"))
        .andExpect(jsonPath("$.data.select.type").value("SINGLE"))
        .andDo(
            document(
                "diagnosis-get-question",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(QUESTION_SUMMARY)
                    .description(QUESTION_DESCRIPTION),
                pathParameters(stepPathParameters()),
                responseFields(questionResponseFields())));

    // 답 저장(단일 선택) — region
    mockMvc
        .perform(
            post("/api/v1/diagnoses/answers")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("region", "SEOUL")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.saved").value(true))
        .andDo(
            document(
                "diagnosis-submit-answer",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(ANSWER_SUMMARY)
                    .description(ANSWER_DESCRIPTION),
                requestFields(answerRequestFields()),
                responseFields(answerSavedResponseFields())));

    // 답 저장 — purpose(③ 단계 서버 분기의 입력이 된다)
    saveAnswer(token, answerJson("purpose", "STUDY"));

    // ① 단계별 질문 조회(step 3) — 서버가 저장된 purpose=STUDY로 university 문항을 선정(클라 분기 아님)
    mockMvc
        .perform(
            get("/api/v1/diagnoses/questions/{step}", 3)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.step").value(3))
        .andExpect(jsonPath("$.data.field").value("university"))
        .andDo(
            document(
                "diagnosis-get-question-step3",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(QUESTION_SUMMARY)
                    .description(QUESTION_DESCRIPTION),
                pathParameters(stepPathParameters()),
                responseFields(questionResponseFields())));

    // ① 단계별 질문 조회(step 3) — purpose=NON_STUDY 분기. 같은 오퍼레이션이 저장된 ②에 따라 다른 문항을 내므로
    // STUDY(university) 예시만으로는 프론트가 district 응답 형태를 알 수 없다. 진행 중 진단은 사용자당 1건이라
    // 위 흐름을 깨지 않도록 별도 사용자로 ①②만 저장한 뒤 조회한다.
    String nonStudyToken = jwtTokenService.issueAccessToken(2L);
    saveAnswer(nonStudyToken, answerJson("region", "SEOUL"));
    saveAnswer(nonStudyToken, answerJson("purpose", "NON_STUDY"));
    mockMvc
        .perform(
            get("/api/v1/diagnoses/questions/{step}", 3)
                .header(HttpHeaders.AUTHORIZATION, bearer(nonStudyToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.step").value(3))
        .andExpect(jsonPath("$.data.field").value("district"))
        .andExpect(jsonPath("$.data.select.type").value("SINGLE"))
        .andExpect(jsonPath("$.data.options[0].code").value("GURO_GU"))
        .andDo(
            document(
                "diagnosis-get-question-step3-district",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(QUESTION_SUMMARY)
                    .description(QUESTION_DESCRIPTION),
                pathParameters(stepPathParameters()),
                responseFields(questionResponseFields())));

    saveAnswer(token, answerJson("university", "SNU_CAU_SOONGSIL"));

    // 답 저장(다중 선택) — conditions 는 codes 배열로 전송
    mockMvc
        .perform(
            post("/api/v1/diagnoses/answers")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"field\":\"conditions\",\"codes\":[\"FEMALE_ONLY\",\"PRIVATE_BATH\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.saved").value(true))
        .andDo(
            document(
                "diagnosis-submit-answer-multi",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(ANSWER_SUMMARY)
                    .description(ANSWER_DESCRIPTION),
                requestFields(answerRequestFields()),
                responseFields(answerSavedResponseFields())));

    // 답 저장(월세 범위) — ⑤만 답의 형태가 다르다. code/codes가 아니라 min·max 두 숫자다
    // (select.type=NUMBER_RANGE, options 비움 — 고정 선택지 목록이라는 가정에서 의도적으로 분리된 예외).
    mockMvc
        .perform(
            post("/api/v1/diagnoses/answers")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerRentJson(300000, 600000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.saved").value(true))
        .andDo(
            document(
                "diagnosis-submit-answer-rent",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(ANSWER_SUMMARY)
                    .description(ANSWER_DESCRIPTION),
                requestFields(answerRequestFields()),
                responseFields(answerSavedResponseFields())));

    saveAnswer(token, answerJson("arcStatus", "ARC_ISSUED"));

    // ③ 진행 중 진단 확정 → 201 Created (Location 헤더 포함)
    String created =
        mockMvc
            .perform(post("/api/v1/diagnoses").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andDo(
                document(
                    "diagnosis-submit",
                    resourceDetails()
                        .tag(ApiDocsTags.DIAGNOSIS)
                        .summary(SUBMIT_SUMMARY)
                        .description(SUBMIT_DESCRIPTION),
                    responseHeaders(
                        headerWithName(HttpHeaders.LOCATION)
                            .description("확정된 진단의 정본 URI — `/api/v1/diagnoses/{diagnosisId}`")),
                    responseFields(submitResponseFields())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long diagnosisId = Long.parseLong(read(created, "data", "diagnosisId"));

    // ④ 내 진단 이력 목록(오프셋 페이지네이션)
    mockMvc
        .perform(
            get("/api/v1/diagnoses")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("page", "0")
                .param("size", "20")
                .param("sort", "submittedAt,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].diagnosisId").value(diagnosisId))
        .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"))
        .andDo(
            document(
                "diagnosis-history",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(HISTORY_SUMMARY)
                    .description(HISTORY_DESCRIPTION),
                queryParameters(historyQueryParameters()),
                responseFields(historyResponseFields())));

    // ⑤ 최근 진단 단건(홈 완료 여부 분기)
    // latest 응답의 요약 필드는 completed=false 케이스 때문에 전부 optional로 문서화한다 —
    // REST Docs의 "선언 안 한 필드가 오면 실패" 방어선이 약해지는 만큼 값 단정으로 되메운다(#151 규약 13).
    mockMvc
        .perform(get("/api/v1/diagnoses/latest").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.completed").value(true))
        .andExpect(jsonPath("$.data.diagnosisId").value(diagnosisId))
        .andExpect(jsonPath("$.data.region").value("SEOUL"))
        .andExpect(jsonPath("$.data.purpose").value("STUDY"))
        .andExpect(jsonPath("$.data.university").value("SNU_CAU_SOONGSIL"))
        .andExpect(jsonPath("$.data.district").isEmpty())
        .andExpect(jsonPath("$.data.conditions[0]").value("FEMALE_ONLY"))
        .andExpect(jsonPath("$.data.monthlyRentMin").value(300000))
        .andExpect(jsonPath("$.data.monthlyRentMax").value(600000))
        .andExpect(jsonPath("$.data.arcStatus").value("ARC_ISSUED"))
        .andExpect(jsonPath("$.data.submittedAt").isString())
        .andDo(
            document(
                "diagnosis-latest",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(LATEST_SUMMARY)
                    .description(LATEST_DESCRIPTION),
                responseFields(latestResponseFields())));

    // ⑤ 최근 진단 단건 — 확정 이력 없음. 홈이 "진단 시작"을 그리는 쪽 분기이며 404가 아니라 200이다.
    // 위 nonStudyToken 사용자는 IN_PROGRESS 진단만 있다 — 진행 중은 대상이 아니라는 계약도 함께 고정한다.
    // completed=false여도 요약 10개 필드는 키가 사라지지 않고 값이 null이다(@JsonInclude 없음).
    // 그래서 이 케이스의 되메움 단정은 doesNotExist()가 아니라 isEmpty()다 — 키 부재면 isEmpty()가 실패한다(규약 13).
    mockMvc
        .perform(
            get("/api/v1/diagnoses/latest")
                .header(HttpHeaders.AUTHORIZATION, bearer(nonStudyToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.completed").value(false))
        .andExpect(jsonPath("$.data.diagnosisId").isEmpty())
        .andExpect(jsonPath("$.data.region").isEmpty())
        .andExpect(jsonPath("$.data.purpose").isEmpty())
        .andExpect(jsonPath("$.data.university").isEmpty())
        .andExpect(jsonPath("$.data.district").isEmpty())
        .andExpect(jsonPath("$.data.conditions").isEmpty())
        .andExpect(jsonPath("$.data.monthlyRentMin").isEmpty())
        .andExpect(jsonPath("$.data.monthlyRentMax").isEmpty())
        .andExpect(jsonPath("$.data.arcStatus").isEmpty())
        .andExpect(jsonPath("$.data.submittedAt").isEmpty())
        .andDo(
            document(
                "diagnosis-latest-not-completed",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(LATEST_SUMMARY)
                    .description(LATEST_DESCRIPTION),
                responseFields(latestResponseFields())));

    // ⑥ 진단 단건 상세(입력 다시 보기, 본인 소유만)
    mockMvc
        .perform(
            get("/api/v1/diagnoses/{diagnosisId}", diagnosisId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.diagnosisId").value(diagnosisId))
        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
        .andDo(
            document(
                "diagnosis-detail",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(DETAIL_SUMMARY)
                    .description(DETAIL_DESCRIPTION),
                pathParameters(diagnosisIdPathParameters()),
                responseFields(detailResponseFields())));

    // ⑦ 추천 결과(매물 + 지도 좌표) — 결과 있음
    given(listingRecommendationService.recommendByCriteria(any()))
        .willReturn(
            pageOf(
                new RecommendedListingView(
                    "6858e2000000000000000001",
                    "Sinchon Co-living House A",
                    new ListingCodeLabelView("CO_LIVING", "Co-living"),
                    550000,
                    700000,
                    1_000_000,
                    1_500_000,
                    "https://cdn.kohere.app/listings/5001/thumb.jpg",
                    37.555134,
                    126.936893,
                    List.of(
                        new ListingCodeLabelView("FEMALE_ONLY", "Female Only"),
                        new ListingCodeLabelView("PRIVATE_BATH", "Private Bath")))));
    // 카드·마커 원소 필드는 0건 스니펫과 헬퍼를 공유하느라 optional이다 — 값 단정으로 계약을 되메운다(규약 13).
    mockMvc
        .perform(
            get("/api/v1/diagnoses/{diagnosisId}/recommendations", diagnosisId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("page", "0")
                .param("size", "20")
                .param("sort", "recommended,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].listingId").value("6858e2000000000000000001"))
        .andExpect(jsonPath("$.data.content[0].title").value("Sinchon Co-living House A"))
        .andExpect(jsonPath("$.data.content[0].type.code").value("CO_LIVING"))
        .andExpect(jsonPath("$.data.content[0].type.label").value("Co-living"))
        .andExpect(jsonPath("$.data.content[0].monthlyRentMin").value(550000))
        .andExpect(jsonPath("$.data.content[0].monthlyRentMax").value(700000))
        .andExpect(jsonPath("$.data.content[0].minDeposit").value(1_000_000))
        .andExpect(jsonPath("$.data.content[0].maxDeposit").value(1_500_000))
        .andExpect(
            jsonPath("$.data.content[0].thumbnailUrl")
                .value("https://cdn.kohere.app/listings/5001/thumb.jpg"))
        .andExpect(jsonPath("$.data.content[0].lat").value(37.555134))
        .andExpect(jsonPath("$.data.content[0].lng").value(126.936893))
        .andExpect(jsonPath("$.data.content[0].conditions[0].code").value("FEMALE_ONLY"))
        .andExpect(jsonPath("$.data.content[0].conditions[0].label").value("Female Only"))
        .andExpect(jsonPath("$.data.markers[0].listingId").value("6858e2000000000000000001"))
        .andExpect(jsonPath("$.data.markers[0].lat").value(37.555134))
        .andExpect(jsonPath("$.data.markers[0].lng").value(126.936893))
        // 결과가 있으면 suggestions는 채워지지 않는다(키는 남고 값이 null이다).
        .andExpect(jsonPath("$.data.suggestions").isEmpty())
        .andDo(
            document(
                "diagnosis-recommendations",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(RECOMMENDATIONS_SUMMARY)
                    .description(RECOMMENDATIONS_DESCRIPTION),
                pathParameters(diagnosisIdPathParameters()),
                queryParameters(recommendationQueryParameters()),
                responseFields(recommendationResponseFields())));

    // ⑦ 추천 결과 — 0건: 빈 목록 + 번역된 조정 제안(suggestions)
    given(listingRecommendationService.recommendByCriteria(any())).willReturn(emptyPage());
    mockMvc
        .perform(
            get("/api/v1/diagnoses/{diagnosisId}/recommendations", diagnosisId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("page", "0")
                .param("size", "20")
                .param("sort", "recommended,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty())
        // 0건이면 카드·마커 원소가 통째로 없다(값이 null인 것이 아니다) — 규약 13.
        .andExpect(jsonPath("$.data.content[0]").doesNotExist())
        .andExpect(jsonPath("$.data.markers[0]").doesNotExist())
        .andExpect(jsonPath("$.data.suggestions.reason").value("NO_MATCH"))
        .andExpect(jsonPath("$.data.suggestions.actions[0].type").value("RELAX_REGION"))
        .andDo(
            document(
                "diagnosis-recommendations-no-match",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(RECOMMENDATIONS_SUMMARY)
                    .description(RECOMMENDATIONS_DESCRIPTION),
                pathParameters(diagnosisIdPathParameters()),
                queryParameters(recommendationQueryParameters()),
                responseFields(recommendationResponseFields())));
  }

  /** 스펙의 "발생 가능한 에러"를 엔드포인트별로 실제 트리거해 스니펫으로 생성하고 status·error.code를 단정한다. */
  @Test
  void generatesDiagnosisErrorSnippets() throws Exception {
    long owner = 100L;
    long stranger = 101L;
    String ownerToken = jwtTokenService.issueAccessToken(owner);
    String strangerToken = jwtTokenService.issueAccessToken(stranger);
    String freshToken = jwtTokenService.issueAccessToken(102L); // 진행 중 진단이 없는 사용자
    String expiredToken = expiredAccessToken(jwtProperties);

    long ownedId = createCompletedDiagnosis(ownerToken);
    long missingId = 9_999_999L;

    // ===== GET /questions/{step} =====
    performWithPathParams(
        get("/api/v1/diagnoses/questions/{step}", 7)
            .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)),
        status().isBadRequest(),
        "INVALID_INPUT",
        "diagnosis-get-question-out-of-range",
        QUESTION_SUMMARY,
        QUESTION_DESCRIPTION,
        stepPathParameters(),
        QUESTION_400);

    performWithPathParams(
        get("/api/v1/diagnoses/questions/{step}", 3)
            .header(HttpHeaders.AUTHORIZATION, bearer(freshToken)),
        status().isBadRequest(),
        "INVALID_INPUT",
        "diagnosis-get-question-purpose-required",
        QUESTION_SUMMARY,
        QUESTION_DESCRIPTION,
        stepPathParameters(),
        QUESTION_400);

    performWithPathParams(
        get("/api/v1/diagnoses/questions/{step}", 1)
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN)),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "diagnosis-get-question-unauthenticated",
        QUESTION_SUMMARY,
        QUESTION_DESCRIPTION,
        stepPathParameters(),
        QUESTION_401);

    performWithPathParams(
        get("/api/v1/diagnoses/questions/{step}", 1)
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "diagnosis-get-question-token-expired",
        QUESTION_SUMMARY,
        QUESTION_DESCRIPTION,
        stepPathParameters(),
        QUESTION_401);

    // ===== POST /answers =====
    perform(
        post("/api/v1/diagnoses/answers")
            .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(answerJson("region", "MARS")),
        status().isBadRequest(),
        "INVALID_INPUT",
        "diagnosis-submit-answer-invalid-input",
        ANSWER_SUMMARY,
        ANSWER_DESCRIPTION,
        ANSWER_400);

    // 본문 누락도 깨진 JSON과 같은 HttpMessageNotReadableException → MALFORMED_REQUEST 다(#151-4).
    // 문서용 스니펫은 본문 없이 남겨 Swagger 요청 예시 중복을 없애고, 「깨진 JSON 거부」 계약은 바로 아래
    // assertError 로 문서화 없이 계속 지킨다.
    perform(
        post("/api/v1/diagnoses/answers")
            .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "diagnosis-submit-answer-malformed",
        ANSWER_SUMMARY,
        ANSWER_DESCRIPTION,
        ANSWER_400);

    assertError(
        mockMvc,
        post("/api/v1/diagnoses/answers")
            .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST");

    // 401 은 시큐리티 필터(JwtAuthenticationFilter)가 DispatcherServlet 이전에 낸다 — 본문과 무관하다.
    perform(
        post("/api/v1/diagnoses/answers")
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "diagnosis-submit-answer-unauthenticated",
        ANSWER_SUMMARY,
        ANSWER_DESCRIPTION,
        ANSWER_401);

    perform(
        post("/api/v1/diagnoses/answers")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "diagnosis-submit-answer-token-expired",
        ANSWER_SUMMARY,
        ANSWER_DESCRIPTION,
        ANSWER_401);

    // ===== POST /diagnoses (확정) =====
    perform(
        post("/api/v1/diagnoses").header(HttpHeaders.AUTHORIZATION, bearer(freshToken)),
        status().isBadRequest(),
        "INVALID_INPUT",
        "diagnosis-submit-no-draft",
        SUBMIT_SUMMARY,
        SUBMIT_DESCRIPTION,
        SUBMIT_400);

    perform(
        post("/api/v1/diagnoses").header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN)),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "diagnosis-submit-unauthenticated",
        SUBMIT_SUMMARY,
        SUBMIT_DESCRIPTION,
        SUBMIT_401);

    perform(
        post("/api/v1/diagnoses").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "diagnosis-submit-token-expired",
        SUBMIT_SUMMARY,
        SUBMIT_DESCRIPTION,
        SUBMIT_401);

    // ===== GET /diagnoses (이력) =====
    perform(
        get("/api/v1/diagnoses")
            .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
            .param("sort", "unknownKey,desc"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "diagnosis-history-invalid-input",
        HISTORY_SUMMARY,
        HISTORY_DESCRIPTION,
        HISTORY_400);

    perform(
        get("/api/v1/diagnoses").header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN)),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "diagnosis-history-unauthenticated",
        HISTORY_SUMMARY,
        HISTORY_DESCRIPTION,
        HISTORY_401);

    perform(
        get("/api/v1/diagnoses").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "diagnosis-history-token-expired",
        HISTORY_SUMMARY,
        HISTORY_DESCRIPTION,
        HISTORY_401);

    // ===== GET /diagnoses/latest =====
    perform(
        get("/api/v1/diagnoses/latest").header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN)),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "diagnosis-latest-unauthenticated",
        LATEST_SUMMARY,
        LATEST_DESCRIPTION,
        LATEST_401);

    perform(
        get("/api/v1/diagnoses/latest").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "diagnosis-latest-token-expired",
        LATEST_SUMMARY,
        LATEST_DESCRIPTION,
        LATEST_401);

    // ===== GET /diagnoses/{id} =====
    performWithPathParams(
        get("/api/v1/diagnoses/{diagnosisId}", ownedId)
            .header(HttpHeaders.AUTHORIZATION, bearer(strangerToken)),
        status().isForbidden(),
        "FORBIDDEN",
        "diagnosis-detail-forbidden",
        DETAIL_SUMMARY,
        DETAIL_DESCRIPTION,
        diagnosisIdPathParameters(),
        DETAIL_403);

    performWithPathParams(
        get("/api/v1/diagnoses/{diagnosisId}", missingId)
            .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)),
        status().isNotFound(),
        "DIAGNOSIS_NOT_FOUND",
        "diagnosis-detail-not-found",
        DETAIL_SUMMARY,
        DETAIL_DESCRIPTION,
        diagnosisIdPathParameters(),
        DETAIL_404);

    performWithPathParams(
        get("/api/v1/diagnoses/{diagnosisId}", ownedId)
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN)),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "diagnosis-detail-unauthenticated",
        DETAIL_SUMMARY,
        DETAIL_DESCRIPTION,
        diagnosisIdPathParameters(),
        DETAIL_401);

    performWithPathParams(
        get("/api/v1/diagnoses/{diagnosisId}", ownedId)
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "diagnosis-detail-token-expired",
        DETAIL_SUMMARY,
        DETAIL_DESCRIPTION,
        diagnosisIdPathParameters(),
        DETAIL_401);

    // ===== GET /diagnoses/{id}/recommendations =====
    performWithPathParams(
        get("/api/v1/diagnoses/{diagnosisId}/recommendations", ownedId)
            .header(HttpHeaders.AUTHORIZATION, bearer(strangerToken)),
        status().isForbidden(),
        "FORBIDDEN",
        "diagnosis-recommendations-forbidden",
        RECOMMENDATIONS_SUMMARY,
        RECOMMENDATIONS_DESCRIPTION,
        diagnosisIdPathParameters(),
        RECOMMENDATIONS_403);

    performWithPathParams(
        get("/api/v1/diagnoses/{diagnosisId}/recommendations", missingId)
            .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)),
        status().isNotFound(),
        "DIAGNOSIS_NOT_FOUND",
        "diagnosis-recommendations-not-found",
        RECOMMENDATIONS_SUMMARY,
        RECOMMENDATIONS_DESCRIPTION,
        diagnosisIdPathParameters(),
        RECOMMENDATIONS_404);

    performWithPathParams(
        get("/api/v1/diagnoses/{diagnosisId}/recommendations", ownedId)
            .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
            .param("sort", "unknownKey,desc"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "diagnosis-recommendations-invalid-input",
        RECOMMENDATIONS_SUMMARY,
        RECOMMENDATIONS_DESCRIPTION,
        diagnosisIdPathParameters(),
        RECOMMENDATIONS_400);

    performWithPathParams(
        get("/api/v1/diagnoses/{diagnosisId}/recommendations", ownedId)
            .header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN)),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "diagnosis-recommendations-unauthenticated",
        RECOMMENDATIONS_SUMMARY,
        RECOMMENDATIONS_DESCRIPTION,
        diagnosisIdPathParameters(),
        RECOMMENDATIONS_401);

    performWithPathParams(
        get("/api/v1/diagnoses/{diagnosisId}/recommendations", ownedId)
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "diagnosis-recommendations-token-expired",
        RECOMMENDATIONS_SUMMARY,
        RECOMMENDATIONS_DESCRIPTION,
        diagnosisIdPathParameters(),
        RECOMMENDATIONS_401);
  }

  // ---- helpers (flow) ----

  private void saveAnswer(String token, String body) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/diagnoses/answers")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  /** 한 사용자의 진단을 단계 답 저장 → 확정까지 수행하고 발급된 diagnosisId를 돌려준다(STUDY 흐름). */
  private long createCompletedDiagnosis(String token) throws Exception {
    saveAnswer(token, answerJson("region", "SEOUL"));
    saveAnswer(token, answerJson("purpose", "STUDY"));
    saveAnswer(token, answerJson("university", "SNU_CAU_SOONGSIL"));
    saveAnswer(token, "{\"field\":\"conditions\",\"codes\":[\"FEMALE_ONLY\"]}");
    saveAnswer(token, answerRentJson(200000, 500000));
    saveAnswer(token, answerJson("arcStatus", "ARC_ISSUED"));
    String created =
        mockMvc
            .perform(post("/api/v1/diagnoses").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return Long.parseLong(read(created, "data", "diagnosisId"));
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
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(expectedCode))
        .andDo(errorSnippet(identifier, ApiDocsTags.DIAGNOSIS, summary, description, errorCodes));
  }

  /** path 변수가 있는 오퍼레이션의 에러 스니펫 — 파라미터 설명이 스니펫 순서에 좌우되지 않도록 함께 선언한다(규약 12). */
  private void performWithPathParams(
      MockHttpServletRequestBuilder request,
      ResultMatcher expectedStatus,
      String expectedCode,
      String identifier,
      String summary,
      String description,
      ParameterDescriptor[] pathParameters,
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
                ApiDocsTags.DIAGNOSIS,
                summary,
                description,
                pathParameters,
                errorCodes));
  }

  // ---- field descriptors ----

  private static List<FieldDescriptor> questionResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.step", JsonFieldType.NUMBER, "질문 단계(1~6) — 요청 path의 `step`과 같다"),
        codeField(
            "data.field",
            QUESTION_FIELD_CODES,
            "답을 보낼 때 쓸 제출 필드명. `POST /api/v1/diagnoses/answers`의 `field`에 그대로 싣는다."
                + " `regionRetry`는 v2 흐름 전용이라 이 엔드포인트로는 내려오지 않는다"),
        field("data.question", JsonFieldType.STRING, "사용자 표시 언어로 번역된 질문 문구(미지원 언어는 영어 폴백)"),
        codeField(
            "data.select.type",
            SELECT_TYPE_CODES,
            "선택 방식 — `SINGLE`은 1택, `MULTI`는 다중, `NUMBER_RANGE`는 선택지가 아니라 `min`·`max` 숫자 2개 입력"),
        field("data.select.max", JsonFieldType.NUMBER, "최대 선택 개수 — ④ `MULTI`는 3, 그 외는 1"),
        field(
            "data.options",
            JsonFieldType.ARRAY,
            "선택지 목록. ⑤ `monthlyRent`(`NUMBER_RANGE`)만 빈 배열이며 클라이언트가 숫자 입력 2개를 그린다"),
        field(
            "data.options[].code",
            JsonFieldType.STRING,
            "선택지 코드 — 언어와 무관하게 같고 확정 검증 enum과 1:1이다. 답의 `code`/`codes`에 그대로 싣는다"),
        field("data.options[].label", JsonFieldType.STRING, "번역된 선택지 표시 라벨"),
        errorNull());
  }

  /**
   * 단계 답 요청 바디. 단일/다중/월세 범위 세 형태가 같은 오퍼레이션이라 <b>기술자를 한 벌로 합친다</b> — 같은 {@code (path, method,
   * status)}에 서로 다른 기술자를 쓰면 {@code (path, type)} dedup에서 한쪽이 조용히 사라진다.
   */
  private static List<FieldDescriptor> answerRequestFields() {
    return List.of(
        codeField(
            "field",
            ANSWER_FIELD_CODES,
            "답을 저장할 제출 필드명 — 직전 질문 응답의 `data.field`를 그대로 싣는다."
                + " v2 전용 `regionRetry`는 이 엔드포인트에서 `INVALID_INPUT`이다"),
        optField(
            "code",
            JsonFieldType.STRING,
            "단일 선택(`SINGLE`) 답의 코드. 선택지 `options[].code` 중 하나이며, 다중·범위 단계에서는 보내지 않는다"),
        optCodeArrayField(
            "codes",
            SELECTABLE_CONDITION_CODES,
            "다중 선택(`MULTI`) 답의 코드 집합 — ④ `conditions` 전용이며 최대 3개·중복 불가."
                + " 파생 조건 `NO_ARC`는 ⑥에서 서버가 만들므로 여기서 고를 수 없다"),
        optField("min", JsonFieldType.NUMBER, "⑤ `monthlyRent` 월세 하한(KRW 정수, 0 이상이고 `max` 이하)"),
        optField("max", JsonFieldType.NUMBER, "⑤ `monthlyRent` 월세 상한(KRW 정수, `min` 이상)"));
  }

  private static List<FieldDescriptor> answerSavedResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.saved", JsonFieldType.BOOLEAN, "진행 중 진단에 저장됨 — 성공 응답에서는 항상 `true`"),
        errorNull());
  }

  private static List<FieldDescriptor> historyResponseFields() {
    List<FieldDescriptor> fields = new ArrayList<>();
    fields.add(field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"));
    fields.add(field("data.content", JsonFieldType.ARRAY, "확정 진단 목록(현재 페이지). 이력이 없으면 빈 배열"));
    fields.addAll(diagnosisSummaryFields("data.content[]."));
    fields.add(
        enumField("data.content[].status", DiagnosisStatus.class, "진단 상태 — 이력에는 `COMPLETED`만 나온다"));
    fields.add(field("data.content[].submittedAt", JsonFieldType.STRING, "확정 시각(ISO-8601 UTC)"));
    fields.addAll(pageFields());
    fields.add(errorNull());
    return List.copyOf(fields);
  }

  /**
   * {@code GET /latest} 전용 응답 기술자.
   *
   * <p>이력·상세와 필드 이름이 같지만 <b>헬퍼를 공유하지 않는다</b> — {@code LatestDiagnosisResponse}에는
   * {@code @JsonInclude}가 없어 {@code completed=false}면 요약 10개 필드가 전부 <b>명시적 null</b>로 실린다. 공유하면
   * 이력·상세의 「항상 있다」 계약까지 함께 풀린다.
   *
   * <p>확정 이력 있음({@code diagnosis-latest})·없음({@code diagnosis-latest-not-completed}) 두 스니펫이 <b>이 헬퍼
   * 하나</b>를 쓴다 — 같은 {@code (path, method, status)}라 기술자가 어차피 하나로 접힌다. 그래서 요약 필드는 전부 {@code
   * optional}이고, 두 스니펫이 값 단정({@code isEmpty()} 포함)으로 계약을 되메운다.
   */
  private static List<FieldDescriptor> latestResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field(
            "data.completed",
            JsonFieldType.BOOLEAN,
            "확정 진단 존재 여부. `false`면 아래 요약 필드가 전부 `null`이다(키는 남는다). 클라이언트는 이 한 필드로 분기한다"),
        optField(
            "data.diagnosisId", JsonFieldType.NUMBER, "최근 확정 진단 식별자. `completed=false`면 `null`"),
        optEnumField("data.region", Region.class, "① 지역. `completed=false`면 `null`"),
        optEnumField("data.purpose", Purpose.class, "② 입국 목적. `completed=false`면 `null`"),
        optEnumField(
            "data.university",
            UniversityGroup.class,
            "③ 대학 그룹 — `purpose=STUDY`일 때만 채워진다. `NON_STUDY`이거나 `completed=false`면 `null`"),
        optEnumField(
            "data.district",
            District.class,
            "③ 지역(구) — `purpose=NON_STUDY`일 때만 채워진다. `STUDY`이거나 `completed=false`면 `null`"),
        optCodeArrayField(
            "data.conditions",
            DIAGNOSIS_CONDITION_CODES,
            "주거 조건 코드 목록(④ 선택 + ⑥ 파생 `NO_ARC`). `completed=false`면 빈 배열이 아니라 `null`"),
        optField(
            "data.monthlyRentMin", JsonFieldType.NUMBER, "⑤ 월세 하한(KRW). `completed=false`면 `null`"),
        optField(
            "data.monthlyRentMax", JsonFieldType.NUMBER, "⑤ 월세 상한(KRW). `completed=false`면 `null`"),
        optEnumField("data.arcStatus", ArcStatus.class, "⑥ ARC 발급 상태. `completed=false`면 `null`"),
        optField(
            "data.submittedAt",
            JsonFieldType.STRING,
            "확정 시각(ISO-8601 UTC). `completed=false`면 `null`"),
        errorNull());
  }

  /**
   * 추천 200 응답. 결과 있음·0건 두 스니펫이 <b>같은 헬퍼</b>를 쓴다 — 같은 {@code (path, method, status)}라 기술자가 어차피 하나로
   * 접히므로, 두 벌을 두면 승자가 파일 순회 순서에 좌우된다. 양쪽에서 사라지는 필드에는 {@code optional}을 건다.
   */
  private static List<FieldDescriptor> recommendationResponseFields() {
    List<FieldDescriptor> fields = new ArrayList<>();
    fields.add(field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"));
    fields.addAll(recommendationContentFields());
    fields.addAll(pageFields());
    fields.add(
        optField(
            "data.suggestions",
            JsonFieldType.OBJECT,
            "조정 제안 — 추천이 0건일 때만 채워지고 결과가 있으면 `null`이다(키는 남는다). v2-3에는 이 필드 자체가 없다"));
    fields.add(
        optCodeField(
            "data.suggestions.reason",
            SUGGESTION_REASON_CODES,
            "조정 제안 사유(언어 무관 코드). 현재 카탈로그에는 `NO_MATCH` 하나뿐이다"));
    fields.add(optField("data.suggestions.message", JsonFieldType.STRING, "사용자 언어로 번역된 안내 메시지"));
    fields.add(optField("data.suggestions.actions", JsonFieldType.ARRAY, "조정 액션 목록"));
    fields.add(
        optCodeField(
            "data.suggestions.actions[].type",
            SUGGESTION_ACTION_CODES,
            "조정 액션 종류(언어 무관 코드) — 화면 분기·로깅에 쓴다"));
    fields.add(
        optField("data.suggestions.actions[].detail", JsonFieldType.STRING, "사용자 언어로 번역된 액션 설명"));
    fields.add(errorNull());
    return List.copyOf(fields);
  }

  // ---- parameter descriptors (varargs — RequestDocumentation has no List overload) ----

  private static ParameterDescriptor[] stepPathParameters() {
    return new ParameterDescriptor[] {
      parameterWithName("step")
          .description(
              "조회할 단계(1~6) — 1=`region`, 2=`purpose`, 3=`university`/`district`(서버가 저장된 `purpose`로 택일),"
                  + " 4=`conditions`, 5=`monthlyRent`, 6=`arcStatus`")
    };
  }

  // ---- seed / fixtures ----

  private void seedQuestions() {
    questionMongoRepository.save(
        question(
            "region",
            "SINGLE",
            1,
            Map.of("en", "Which region will you live in?", "ko", "어느 지역에서 거주할 예정인가요?"),
            List.of(
                option("SEOUL", "Seoul", "서울"),
                option("BUSAN", "Busan", "부산"),
                option("GYEONGGI", "Gyeonggi", "경기"))));
    questionMongoRepository.save(
        question(
            "university",
            "SINGLE",
            1,
            Map.of("en", "Which university do you attend?", "ko", "어느 대학교에 다니나요?"),
            List.of(
                option("SNU_CAU_SOONGSIL", "Seoul National · Chung-Ang · Soongsil", "서울대·중앙대·숭실대"),
                option("HUFS_KHU_KOREA", "HUFS · Kyung Hee · Korea Univ.", "한국외대·경희대·고려대"))));
    questionMongoRepository.save(
        question(
            "district",
            "SINGLE",
            1,
            Map.of("en", "Which district will you live in?", "ko", "어느 지역(구)에서 거주할 예정인가요?"),
            List.of(option("GURO_GU", "Guro-gu", "구로구"), option("GWANAK_GU", "Gwanak-gu", "관악구"))));
  }

  private void seedSuggestion() {
    suggestionMongoRepository.save(
        DiagnosisSuggestionDocument.builder()
            .id("NO_MATCH")
            .message(
                Map.of(
                    "en", "No listings matched your criteria. Try adjusting the options below.",
                    "ko", "조건에 맞는 매물이 없습니다. 아래 항목을 조정해 보세요."))
            .actions(
                List.of(
                    suggestionAction("RELAX_REGION", "Widen the region.", "지역 범위를 넓혀 보세요."),
                    suggestionAction(
                        "RELAX_CONDITIONS", "Reduce housing conditions.", "주거 조건을 줄여 보세요."),
                    suggestionAction(
                        "INCREASE_BUDGET", "Increase the monthly budget.", "월 예산을 높여 보세요.")))
            .build());
  }

  private static DiagnosisQuestionDocument question(
      String field,
      String selectType,
      int max,
      Map<String, String> question,
      List<OptionSpec> options) {
    return DiagnosisQuestionDocument.builder()
        .field(field)
        .active(true)
        .question(question)
        .select(SelectSpec.builder().type(selectType).max(max).build())
        .options(options)
        .build();
  }

  private static OptionSpec option(String code, String en, String ko) {
    return OptionSpec.builder().code(code).label(Map.of("en", en, "ko", ko)).build();
  }

  private static ActionSpec suggestionAction(String type, String en, String ko) {
    return ActionSpec.builder().type(type).detail(Map.of("en", en, "ko", ko)).build();
  }

  private static PageResponse<RecommendedListingView> emptyPage() {
    return PageResponse.of(List.of(), new PageInfo(0, 20, 0L, 0, false));
  }

  private static PageResponse<RecommendedListingView> pageOf(RecommendedListingView... views) {
    List<RecommendedListingView> content = List.of(views);
    return PageResponse.of(content, new PageInfo(0, 20, content.size(), 1, false));
  }

  // ---- json helpers ----

  private String read(String json, String... path) throws Exception {
    JsonNode node = objectMapper.readTree(json);
    for (String key : path) {
      node = node.path(key);
    }
    return node.asText();
  }

  private static String answerJson(String field, String code) {
    return "{\"field\":\"" + field + "\",\"code\":\"" + code + "\"}";
  }

  private static String answerRentJson(int min, int max) {
    return "{\"field\":\"monthlyRent\",\"min\":" + min + ",\"max\":" + max + "}";
  }
}
