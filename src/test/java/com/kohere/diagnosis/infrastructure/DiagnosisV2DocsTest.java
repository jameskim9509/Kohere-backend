package com.kohere.diagnosis.infrastructure;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails;
import static com.kohere.docs.ApiDocsErrors.errorSnippet;
import static com.kohere.docs.ApiDocsFields.codeField;
import static com.kohere.docs.ApiDocsFields.enumField;
import static com.kohere.docs.ApiDocsFields.errorNull;
import static com.kohere.docs.ApiDocsFields.field;
import static com.kohere.docs.ApiDocsFields.optCodeArrayField;
import static com.kohere.docs.ApiDocsFields.optCodeField;
import static com.kohere.docs.ApiDocsFields.optField;
import static com.kohere.docs.DiagnosisDocsFields.LANGUAGE_NOTE;
import static com.kohere.docs.DiagnosisDocsFields.QUESTION_FIELD_CODES;
import static com.kohere.docs.DiagnosisDocsFields.QUESTION_TABLE;
import static com.kohere.docs.DiagnosisDocsFields.SEED_NOTE;
import static com.kohere.docs.DiagnosisDocsFields.SELECTABLE_CONDITION_CODES;
import static com.kohere.docs.DiagnosisDocsFields.SELECT_TYPE_CODES;
import static com.kohere.docs.DiagnosisDocsFields.pageFields;
import static com.kohere.docs.DiagnosisDocsFields.recommendationContentFields;
import static com.kohere.docs.DiagnosisDocsFields.recommendationQueryParameters;
import static com.kohere.docs.DocsTokens.bearer;
import static com.kohere.docs.DocsTokens.expiredAccessToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
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
import com.kohere.diagnosis.application.dto.FlowResultCode;
import com.kohere.diagnosis.application.dto.RecommendationResultCode;
import com.kohere.diagnosis.infrastructure.DiagnosisQuestionDocument.OptionSpec;
import com.kohere.diagnosis.infrastructure.DiagnosisQuestionDocument.SelectSpec;
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
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.request.ParameterDescriptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * v2 서버 주도 진단 흐름({@code /api/v2/diagnoses/*})의 Spring REST Docs 스니펫 생성 테스트(issue #157·ADR-0036).
 * 시작·답 적용·확정·추천 조회의 성공 응답과 스펙(02-diagnosis-recommendation.md §v2)에 정의된 에러 응답을 {@code
 * build/generated-snippets}에 생성해 OpenAPI3 명세(Swagger UI)에 합류시킨다. v1 스니펫은 {@link DiagnosisDocsTest}가
 * 담당한다.
 *
 * <p><b>문서 규약</b>(#151) — 이 파일이 문서화하는 오퍼레이션은 셋({@code POST /start} · {@code POST /next} · {@code
 * GET /{id}/recommendations})뿐이고, {@code resultCode}·회원/게스트별 스니펫은 전부 그 셋 중 하나로 병합된다. 그래서
 * summary·description은 <b>오퍼레이션당 한 벌</b>만 두고 케이스 구분은 document identifier(= Swagger Examples 드롭다운
 * 항목명)로 한다. 태그도 v1과 같은 {@link ApiDocsTags#DIAGNOSIS} 하나다 — 나누면 공유 오퍼레이션이 두 그룹에 중복 노출된다.
 *
 * <p><b>흐름 응답은 태그드 유니온</b>이라 {@code resultCode}별로 스니펫을 따로 내되({@code NEXT_QUESTION}·{@code
 * COMPLETED}·{@code RESTART}·{@code TERMINATED}) <b>필드 기술자는 한 벌로 합친다</b> — 같은 {@code (path, method,
 * status)}의 기술자는 어차피 {@code (path, type)} 기준으로 하나로 접히므로, 여러 벌을 두면 승자가 파일 순회 순서에 좌우된다. 채워지지 않는
 * payload는 {@code NON_NULL}로 생략되므로 그 필드들은 {@code optional}이고, 각 스니펫이 {@code doesNotExist()}로 「이
 * 케이스에는 없다」를 못 박는다.
 *
 * <p>cross-module 협력(listing 추천·user 표시 언어)은 {@code @MockitoBean}으로 대체하고 access 토큰은 {@link
 * JwtTokenService}로 직접 발급한다(test-strategy §4, {@link DiagnosisDocsTest}와 동일 전략). MongoDB(문항
 * 카탈로그·진단·진행 세션)는 실제 컨테이너로 구동한다. Mongock 시더는 {@code test} 프로파일에서 비활성이라 이 테스트가 직접 카탈로그를 시드한다 — v2는
 * 6슬롯 문항 전부와 ① 지역 0건 예외질문({@code regionRetry})까지 필요하다.
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
@Testcontainers
@Import(TestcontainersConfiguration.class)
class DiagnosisV2DocsTest {

  @Container @ServiceConnection static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

  private static final String MALFORMED_BODY = "{ \"oops\" }";

  /** 게스트 세션 키 에코 헤더(#181). 발급은 {@code /start} 응답이고 소비처는 {@code /next}·추천 조회다. */
  private static final String GUEST_SESSION_HEADER = "X-Guest-Session-Id";

  /** {@code POST /start}는 언제나 ① 지역 질문 하나만 낸다 — 다른 결과코드로 갈 수 없다. */
  private static final List<String> START_RESULT_CODES = List.of("NEXT_QUESTION");

  // ---- 오퍼레이션 상수: POST /api/v2/diagnoses/start ----

  private static final String V2_START_SUMMARY = "v2 진단 시작";
  private static final String V2_START_DESCRIPTION =
      """
      진단을 처음부터 시작하고 ① 지역 질문을 받는다. 시작 시점은 클라이언트가 정한다.

      **인증**

      - 인증은 선택이다. 토큰을 보내면 회원, 안 보내면 게스트로 처리한다.
      - 게스트로 호출하면 응답 `data.guestSessionId`에 세션 키(`anonymous<uuid>`)가 실린다. **키가 내려오는 유일한 지점**이며 회원 응답에서는 이 필드가 통째로 생략된다.
      - 클라이언트는 그 키를 보관했다가 이후 `POST /next`와 추천 조회에 `X-Guest-Session-Id` 헤더로 되돌려보낸다.
      - 요청에 `X-Guest-Session-Id`를 실어도 무시하고 새 키를 발급한다.
      - 위조·형식 오류 토큰은 게스트와 같은 취급이지만 **만료 토큰만은 401**이다(조용한 게스트 강등 금지).

      **동작 규칙**

      - 요청 본문이 없다.
      - 진행 중 세션이 있어도 버리고 새 세션을 만든다 — 다시 시작하면 언제나 ① 지역부터다.
      - 응답은 항상 `resultCode=NEXT_QUESTION`이고 `question.field=region`이다. `diagnosisId`는 실리지 않는다.
      - """
          + LANGUAGE_NOTE
          + """
       게스트는 `users` 행이 없어 `en` 고정이다.
      - """
          + SEED_NOTE
          + """


      **에러 코드**

      - `401 TOKEN_EXPIRED` — 액세스 토큰 만료. 토큰 미전송·위조는 게스트로 처리하므로 `UNAUTHENTICATED`는 이 엔드포인트에서 발생하지 않는다
      """;
  private static final String[] V2_START_401 = {"TOKEN_EXPIRED"};

  // ---- 오퍼레이션 상수: POST /api/v2/diagnoses/next ----

  private static final String V2_NEXT_SUMMARY = "v2 문항 답 적용";
  private static final String V2_NEXT_DESCRIPTION =
      """
      현재 문항의 답 1개를 적용하고 서버가 정한 다음 결과를 결과코드로 받는다.

      **인증**

      - 인증은 선택이다. 회원은 `Authorization` 토큰으로, 게스트는 `X-Guest-Session-Id` 헤더로 진행 세션을 찾는다.
      - 게스트가 헤더를 빠뜨렸거나 남의 키를 보내면 세션을 못 찾아 `400 DIAGNOSIS_SESSION_NOT_FOUND`다 — 남의 세션에 닿지 않는다.
      - 만료 토큰만 401이며 위조 토큰은 게스트로 처리된다.

      **결과코드별 payload**

      | `resultCode` | 의미 | 실리는 필드 |
      | --- | --- | --- |
      | `NEXT_QUESTION` | 다음 질문이 남음. ① 지역 0건 예외질문(`field=regionRetry`)도 이 코드다 | `question` |
      | `COMPLETED` | 마지막 슬롯(⑥ `arcStatus`)까지 답해 서버가 자동 확정 | `diagnosisId` |
      | `RESTART` | 지역 예외질문에 "예"(`YES`) — 클라이언트가 `POST /start`로 재시도(세션 삭제) | 없음 |
      | `TERMINATED` | 지역 예외질문에 "아니오"(`NO`) — 진단 종료(세션 삭제). 에러가 아니라 정상 결과다 | 없음 |

      - 채워지지 않는 payload는 값이 `null`인 것이 아니라 **필드 자체가 생략된다**(`NON_NULL`).
      - `COMPLETED`에도 추천 매물은 실리지 않는다. 서버는 이 시점에 매칭 유무조차 확인하지 않으며, 클라이언트가 `diagnosisId`로 `GET /api/v2/diagnoses/{diagnosisId}/recommendations`를 별도 호출한다.
      - 매칭 0건은 이 응답이 아니라 그 추천 응답의 `resultCode=NO_MATCH`로 드러난다.

      **문항과 답의 형태**

      """
          + QUESTION_TABLE
          + """

      - `step`은 클라이언트가 지정하지 않는다. 서버가 낸 문항의 `field`를 그대로 요청 `field`에 싣는다(다르면 `INVALID_INPUT`).
      - ③ 대학/지역은 저장된 ② `purpose`로 서버가 택일한다.
      - ① 지역 답 직후 그 지역 매물이 0건이면 서버가 `field=regionRetry` 예외질문(`YES`/`NO`)을 끼워 넣는다 — 서버가 미리 필터링하는 유일한 지점이다.
      - """
          + LANGUAGE_NOTE
          + """

      - """
          + SEED_NOTE
          + """


      **에러 코드**

      - `400 DIAGNOSIS_SESSION_NOT_FOUND` — 진행 중 세션 없이 호출(앱 재시작·터미널 이후 재전송·게스트 키 누락/불일치) → `POST /start`로 복구
      - `400 INVALID_INPUT` — `field` 없음, 현재 문항과 다른 `field`, 미정의 코드, `regionRetry`가 `YES`/`NO` 아님
      - `400 MALFORMED_REQUEST` — 본문 JSON 해석 불가(검증 이전)
      - `401 TOKEN_EXPIRED` — 액세스 토큰 만료. 토큰 미전송·위조는 게스트로 처리하므로 `UNAUTHENTICATED`는 발생하지 않는다
      """;
  private static final String[] V2_NEXT_400 = {
    "DIAGNOSIS_SESSION_NOT_FOUND", "INVALID_INPUT", "MALFORMED_REQUEST"
  };
  private static final String[] V2_NEXT_401 = {"TOKEN_EXPIRED"};

  // ---- 오퍼레이션 상수: GET /api/v2/diagnoses/{diagnosisId}/recommendations ----

  private static final String V2_RECOMMENDATIONS_SUMMARY = "v2 진단 결과 추천 매물";
  private static final String V2_RECOMMENDATIONS_DESCRIPTION =
      """
      v2에서 확정된 진단 조건에 맞는 매물 카드와 지도 마커를 조회한다. 조회 시점·페이지·정렬은 클라이언트가 정한다.

      **인증**

      - 인증은 선택이다. 회원은 `Authorization` 토큰으로, 게스트는 `X-Guest-Session-Id` 헤더로 소유권을 증명한다.
      - 소유는 신원 **종류와 값이 모두** 같을 때만 인정된다 — 게스트↔회원 교차 조회는 양방향 모두 `403 FORBIDDEN`이고, 신원 없는 요청도 403이다.
      - 회원 요청에 게스트 키가 실려 와도 무시한다(키를 훔쳐도 통하지 않는다).

      **화면 구성**

      - 매물 카드는 `content[]`로, 지도 마커는 `markers[]`로 그리고 둘은 같은 `listingId`로 연결한다.
      - 매물 유형과 조건 배지는 `type.label`·`conditions[].label`을 표시하고, 필터 재요청이나 내부 비교에는 같은 객체의 `code`를 쓴다.
      - `title`과 `label`은 사용자 표시 언어로 선택되어 온다(게스트는 `en` 고정).
      - 정렬 허용 키는 `recommended`·`price`·`distance`이며 기본은 `recommended,desc`다.

      **매칭 결과코드**

      - `resultCode`는 항상 실린다 — `MATCHED`(매물 있음) 또는 `NO_MATCH`(0건).
      - `NO_MATCH`는 에러가 아니며 `content=[]`·`markers=[]`다.
      - v1 §7과 달리 **조정 제안(`suggestions`) 필드가 없다** — 사유만 `resultCode`로 준다.

      **에러 코드**

      - `400 INVALID_INPUT` — `page`/`size` 범위 위반, 허용되지 않은 `sort` 키 또는 방향
      - `401 TOKEN_EXPIRED` — 액세스 토큰 만료. 토큰 미전송·위조는 게스트로 처리하므로 `UNAUTHENTICATED`는 발생하지 않는다
      - `403 FORBIDDEN` — 타인 소유 진단, 게스트↔회원 교차 조회, 게스트 키 미전송
      - `404 DIAGNOSIS_NOT_FOUND` — 진단이 존재하지 않거나 폐기 기록
      """;
  private static final String[] V2_RECOMMENDATIONS_400 = {"INVALID_INPUT"};
  private static final String[] V2_RECOMMENDATIONS_401 = {"TOKEN_EXPIRED"};
  private static final String[] V2_RECOMMENDATIONS_403 = {"FORBIDDEN"};
  private static final String[] V2_RECOMMENDATIONS_404 = {"DIAGNOSIS_NOT_FOUND"};

  // 서명이 깨진(다른 키로 서명) access 토큰. 401 UNAUTHENTICATED 를 유발하면서도 구조상 JWT 라, restdocs-api-spec 이
  // 무인증 예시에서도 bearerAuthJWT 보안 스킴을 도출하게 한다(비결정적 스니펫 병합 순서와 무관하게 Swagger 자물쇠 유지 —
  // DiagnosisDocsTest 와 동일 처리).
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
  @Autowired private DiagnosisFlowSessionMongoRepository flowSessionMongoRepository;

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
    flowSessionMongoRepository.deleteAll();
    seedQuestions();
    // 표시 언어는 user 공개 query로 취득(ADR-0029) — users.lang='ko'인 회원을 가정해 한국어 라벨을 내려받는다.
    given(userAccountService.getLanguage(anyLong())).willReturn("ko");
    // 기본은 "그 지역에 매물이 있음" — ① 지역 조기 게이트를 통과시킨다. 0건 경로를 보는 테스트가 개별로 덮어쓴다.
    given(listingRecommendationService.recommendByCriteria(any())).willReturn(pageOf(SAMPLE_VIEW));
    given(listingRecommendationService.recommendByCriteria(any(), anyString()))
        .willAnswer(
            invocation ->
                listingRecommendationService.recommendByCriteria(invocation.getArgument(0)));
  }

  /** v2-1 시작 → v2-2 정본 6슬롯 진행 → 자동 확정 → v2-3 추천 조회(MATCHED·NO_MATCH)까지의 성공 경로. */
  @Test
  void generatesV2FlowSnippets() throws Exception {
    long userId = 1L;
    String token = jwtTokenService.issueAccessToken(userId);

    // v2-1. POST /start — 진행 중 세션이 있어도 버리고 처음부터. 언제나 ① 지역 질문이다(본문 없음).
    mockMvc
        .perform(post("/api/v2/diagnoses/start").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resultCode").value("NEXT_QUESTION"))
        .andExpect(jsonPath("$.data.question.field").value("region"))
        .andExpect(jsonPath("$.data.question.step").value(1))
        .andExpect(jsonPath("$.data.question.select.type").value("SINGLE"))
        .andExpect(jsonPath("$.data.question.options[0].code").value("SEOUL"))
        // 회원 응답에는 게스트 세션 키가 실리지 않는다(NON_NULL — 값 null이 아니라 필드 자체가 없다).
        .andExpect(jsonPath("$.data.guestSessionId").doesNotExist())
        .andDo(
            document(
                "diagnosis-v2-start",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(V2_START_SUMMARY)
                    .description(V2_START_DESCRIPTION),
                responseFields(startResponseFields())));

    // v2-2. POST /next — ① 지역 답 적용 → 그 지역에 매물이 있으므로 정본 순서상 다음 슬롯(② 목적) 질문.
    mockMvc
        .perform(
            post("/api/v2/diagnoses/next")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("region", "SEOUL")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resultCode").value("NEXT_QUESTION"))
        .andExpect(jsonPath("$.data.question.field").value("purpose"))
        .andExpect(jsonPath("$.data.question.step").value(2))
        .andExpect(jsonPath("$.data.question.options[0].code").value("STUDY"))
        .andExpect(jsonPath("$.data.diagnosisId").doesNotExist())
        .andDo(
            document(
                "diagnosis-v2-next-question",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(V2_NEXT_SUMMARY)
                    .description(V2_NEXT_DESCRIPTION),
                requestFields(nextRequestFields()),
                responseFields(nextResponseFields())));

    // ③ 대학/지역 분기는 서버가 저장된 purpose로 택일한다 — STUDY면 university 문항이 나온다(클라 분기 아님).
    next(token, answerJson("purpose", "STUDY"))
        .andExpect(jsonPath("$.data.question.field").value("university"));
    next(token, answerJson("university", "SNU_CAU_SOONGSIL"))
        .andExpect(jsonPath("$.data.question.field").value("conditions"));
    // ④ 주거 조건은 codes 배열로 답한다. 그 응답이 ⑤ 월세 문항인데, 6슬롯 중 이것만
    // select.type=NUMBER_RANGE·options 빈 배열이다 — "질문은 곧 선택지 목록"이라는 가정에서 의도적으로
    // 분리된 예외라(US-2-5), 클라가 피커가 아니라 숫자 입력 2개를 그려야 하는 유일한 문항이다.
    // 다른 스니펫의 문항이 전부 SINGLE+선택지라 이 모양을 예시로 남기지 않으면 명세 어디에도 없다.
    mockMvc
        .perform(
            post("/api/v2/diagnoses/next")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"field\":\"conditions\",\"codes\":[\"FEMALE_ONLY\",\"PRIVATE_BATH\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resultCode").value("NEXT_QUESTION"))
        .andExpect(jsonPath("$.data.question.field").value("monthlyRent"))
        .andExpect(jsonPath("$.data.question.step").value(5))
        .andExpect(jsonPath("$.data.question.select.type").value("NUMBER_RANGE"))
        .andExpect(jsonPath("$.data.question.options").isEmpty())
        .andExpect(jsonPath("$.data.question.options[0]").doesNotExist())
        .andExpect(jsonPath("$.data.diagnosisId").doesNotExist())
        .andDo(
            document(
                "diagnosis-v2-next-conditions",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(V2_NEXT_SUMMARY)
                    .description(V2_NEXT_DESCRIPTION),
                requestFields(nextRequestFields()),
                responseFields(nextResponseFields())));

    // ⑤ 월세 범위만 답의 형태가 다르다 — code/codes가 아니라 min·max 두 숫자다(select.type=NUMBER_RANGE).
    // 다른 스니펫이 모두 code 형태라 예시를 따로 남기지 않으면 Swagger 요청 예시에 이 형태가 아예 없다.
    mockMvc
        .perform(
            post("/api/v2/diagnoses/next")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerRentJson(300000, 600000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resultCode").value("NEXT_QUESTION"))
        .andExpect(jsonPath("$.data.question.field").value("arcStatus"))
        .andExpect(jsonPath("$.data.question.step").value(6))
        .andExpect(jsonPath("$.data.question.options[0].code").value("ARC_ISSUED"))
        .andExpect(jsonPath("$.data.diagnosisId").doesNotExist())
        .andDo(
            document(
                "diagnosis-v2-next-monthly-rent",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(V2_NEXT_SUMMARY)
                    .description(V2_NEXT_DESCRIPTION),
                requestFields(nextRequestFields()),
                responseFields(nextResponseFields())));

    // v2-2. 마지막 슬롯(⑥ ARC)을 답하면 빌더 완성 → 서버가 자동 확정하고 diagnosisId만 준다.
    // 매칭 유무는 확인하지 않는다 — 추천은 클라이언트가 시점을 정해 v2-3으로 별도 조회한다.
    String completed =
        mockMvc
            .perform(
                post("/api/v2/diagnoses/next")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(answerJson("arcStatus", "ARC_ISSUED")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.resultCode").value("COMPLETED"))
            .andExpect(jsonPath("$.data.question").doesNotExist())
            .andDo(
                document(
                    "diagnosis-v2-next-completed",
                    resourceDetails()
                        .tag(ApiDocsTags.DIAGNOSIS)
                        .summary(V2_NEXT_SUMMARY)
                        .description(V2_NEXT_DESCRIPTION),
                    requestFields(nextRequestFields()),
                    responseFields(nextResponseFields())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long diagnosisId = Long.parseLong(read(completed, "data", "diagnosisId"));

    // v2-3. GET /{id}/recommendations — 매칭 있음(MATCHED). 이 호출 자체가 "매물을 받겠다"는 클라이언트의 결정이다.
    mockMvc
        .perform(
            get("/api/v2/diagnoses/{diagnosisId}/recommendations", diagnosisId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("page", "0")
                .param("size", "20")
                .param("sort", "recommended,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resultCode").value("MATCHED"))
        // listing 뷰→응답 매핑은 같은 타입이 줄줄이 늘어선 위치 인자(문자열 4·금액 4·좌표 2)라 필드가 서로
        // 뒤바뀌어도 타입은 그대로다 — 스니펫이 곧 클라이언트 계약이므로 값까지 못 박아 자리바꿈을 잡는다.
        // 0건 스니펫과 필드 기술자를 공유하느라 원소가 optional이라 이 단정이 계약을 되메운다(#151 규약 13).
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
        .andExpect(jsonPath("$.data.content[0].conditions[1].code").value("PRIVATE_BATH"))
        .andExpect(jsonPath("$.data.markers[0].listingId").value("6858e2000000000000000001"))
        .andExpect(jsonPath("$.data.markers[0].lat").value(37.555134))
        .andExpect(jsonPath("$.data.markers[0].lng").value(126.936893))
        .andDo(
            document(
                "diagnosis-v2-recommendations",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(V2_RECOMMENDATIONS_SUMMARY)
                    .description(V2_RECOMMENDATIONS_DESCRIPTION),
                pathParameters(v2DiagnosisIdPathParameters()),
                queryParameters(recommendationQueryParameters()),
                responseFields(v2RecommendationFields())));

    // v2-3. 매칭 0건(NO_MATCH) — 에러가 아니라 정상 결과이며, v1 §7과 달리 조정 제안(suggestions)이 없다.
    given(listingRecommendationService.recommendByCriteria(any())).willReturn(emptyPage());
    mockMvc
        .perform(
            get("/api/v2/diagnoses/{diagnosisId}/recommendations", diagnosisId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("page", "0")
                .param("size", "20")
                .param("sort", "recommended,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resultCode").value("NO_MATCH"))
        .andExpect(jsonPath("$.data.content").isEmpty())
        // 0건이면 카드·마커 원소가 통째로 없다(값이 null인 것이 아니다) — 규약 13.
        .andExpect(jsonPath("$.data.content[0]").doesNotExist())
        .andExpect(jsonPath("$.data.markers[0]").doesNotExist())
        .andExpect(jsonPath("$.data.suggestions").doesNotExist())
        .andDo(
            document(
                "diagnosis-v2-recommendations-no-match",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(V2_RECOMMENDATIONS_SUMMARY)
                    .description(V2_RECOMMENDATIONS_DESCRIPTION),
                pathParameters(v2DiagnosisIdPathParameters()),
                queryParameters(recommendationQueryParameters()),
                responseFields(v2RecommendationFields())));
  }

  /**
   * ① 지역 0건 예외질문(서버가 미리 필터링하는 유일한 지점)과 그 예/아니오 응답의 흐름 제어 코드.
   *
   * <p>예외질문 자체는 별도 결과코드가 아니라 카탈로그의 <b>일반 질문</b>({@code field=regionRetry})으로 내려가고, 그 응답에만 클라이언트가 행할
   * 행위를 코드로 알린다(예={@code RESTART} · 아니오={@code TERMINATED}).
   */
  @Test
  void generatesV2RegionRetrySnippets() throws Exception {
    String retryToken = jwtTokenService.issueAccessToken(10L);
    String endToken = jwtTokenService.issueAccessToken(11L);
    // 그 지역에 매물이 하나도 없는 상황 — ① 지역 답 직후 서버가 조기에 걸러낸다.
    given(listingRecommendationService.recommendByCriteria(any())).willReturn(emptyPage());

    start(retryToken);
    mockMvc
        .perform(
            post("/api/v2/diagnoses/next")
                .header(HttpHeaders.AUTHORIZATION, bearer(retryToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("region", "BUSAN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resultCode").value("NEXT_QUESTION"))
        .andExpect(jsonPath("$.data.question.field").value("regionRetry"))
        .andExpect(jsonPath("$.data.question.step").value(1))
        .andExpect(jsonPath("$.data.question.options[0].code").value("YES"))
        .andExpect(jsonPath("$.data.diagnosisId").doesNotExist())
        .andDo(
            document(
                "diagnosis-v2-next-region-retry",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(V2_NEXT_SUMMARY)
                    .description(V2_NEXT_DESCRIPTION),
                requestFields(nextRequestFields()),
                responseFields(nextResponseFields())));

    // 예 → 클라이언트가 POST /start로 처음부터 재시도한다(세션 삭제, 코드만).
    mockMvc
        .perform(
            post("/api/v2/diagnoses/next")
                .header(HttpHeaders.AUTHORIZATION, bearer(retryToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("regionRetry", "YES")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resultCode").value("RESTART"))
        .andExpect(jsonPath("$.data.question").doesNotExist())
        .andExpect(jsonPath("$.data.diagnosisId").doesNotExist())
        .andDo(
            document(
                "diagnosis-v2-next-restart",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(V2_NEXT_SUMMARY)
                    .description(V2_NEXT_DESCRIPTION),
                requestFields(nextRequestFields()),
                responseFields(nextResponseFields())));

    // 아니오 → 진단 종료. 에러가 아니라 정상 결과라 error가 아닌 resultCode로 표현한다.
    start(endToken);
    next(endToken, answerJson("region", "BUSAN"))
        .andExpect(jsonPath("$.data.question.field").value("regionRetry"));
    mockMvc
        .perform(
            post("/api/v2/diagnoses/next")
                .header(HttpHeaders.AUTHORIZATION, bearer(endToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("regionRetry", "NO")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resultCode").value("TERMINATED"))
        .andExpect(jsonPath("$.data.question").doesNotExist())
        .andExpect(jsonPath("$.data.diagnosisId").doesNotExist())
        .andDo(
            document(
                "diagnosis-v2-next-terminated",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(V2_NEXT_SUMMARY)
                    .description(V2_NEXT_DESCRIPTION),
                requestFields(nextRequestFields()),
                responseFields(nextResponseFields())));
  }

  /**
   * 게스트 흐름 스니펫(#181) — 회원 스니펫({@link #generatesV2FlowSnippets})과 같은 오퍼레이션이라 restdocs-api-spec이 예시를
   * 함께 병합한다. 게스트 예시가 있어야 Swagger에 <b>{@code /start} 응답의 {@code guestSessionId}</b>와 <b>{@code
   * /next}·추천의 {@code X-Guest-Session-Id} 요청 헤더</b>가 드러난다(회원 시점만 문서화하면 비회원 사용법이 명세에 없다).
   */
  @Test
  void generatesV2GuestSnippets() throws Exception {
    given(listingRecommendationService.recommendByCriteria(any())).willReturn(pageOf(SAMPLE_VIEW));

    // ① POST /start (토큰 없음) — 게스트 세션 키를 발급해 응답에 싣는다. 회원 응답에는 없는 필드다(NON_NULL).
    String startBody =
        mockMvc
            .perform(post("/api/v2/diagnoses/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.resultCode").value("NEXT_QUESTION"))
            .andExpect(jsonPath("$.data.question.field").value("region"))
            .andExpect(jsonPath("$.data.question.step").value(1))
            .andExpect(jsonPath("$.data.question.select.type").value("SINGLE"))
            .andExpect(jsonPath("$.data.question.options[0].code").value("SEOUL"))
            .andExpect(jsonPath("$.data.guestSessionId").exists())
            .andDo(
                document(
                    "diagnosis-v2-start-guest",
                    resourceDetails()
                        .tag(ApiDocsTags.DIAGNOSIS)
                        .summary(V2_START_SUMMARY)
                        .description(V2_START_DESCRIPTION),
                    responseFields(startResponseFields())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String guestKey = read(startBody, "data", "guestSessionId");

    // ② POST /next (X-Guest-Session-Id 헤더) — 게스트는 이 헤더가 유일한 세션 조회 키다.
    mockMvc
        .perform(
            post("/api/v2/diagnoses/next")
                .header(GUEST_SESSION_HEADER, guestKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("region", "SEOUL")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.question.field").value("purpose"))
        .andExpect(jsonPath("$.data.question.step").value(2))
        .andExpect(jsonPath("$.data.question.options[0].code").value("STUDY"))
        .andExpect(jsonPath("$.data.diagnosisId").doesNotExist())
        .andDo(
            document(
                "diagnosis-v2-next-guest",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(V2_NEXT_SUMMARY)
                    .description(V2_NEXT_DESCRIPTION),
                requestHeaders(guestSessionHeader()),
                requestFields(nextRequestFields()),
                responseFields(nextResponseFields())));

    // ③ 확정까지 진행 → GET /{id}/recommendations 도 헤더만으로 소유권을 증명한다(① 지역은 위에서 이미 답했다).
    long diagnosisId = completeGuestFlowAfterRegion(guestKey);
    mockMvc
        .perform(
            get("/api/v2/diagnoses/{diagnosisId}/recommendations", diagnosisId)
                .header(GUEST_SESSION_HEADER, guestKey)
                .param("page", "0")
                .param("size", "20")
                .param("sort", "recommended,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resultCode").value("MATCHED"))
        .andExpect(jsonPath("$.data.content[0].listingId").value("6858e2000000000000000001"))
        .andDo(
            document(
                "diagnosis-v2-recommendations-guest",
                resourceDetails()
                    .tag(ApiDocsTags.DIAGNOSIS)
                    .summary(V2_RECOMMENDATIONS_SUMMARY)
                    .description(V2_RECOMMENDATIONS_DESCRIPTION),
                requestHeaders(guestSessionHeader()),
                pathParameters(v2DiagnosisIdPathParameters()),
                queryParameters(recommendationQueryParameters()),
                responseFields(v2RecommendationFields())));
  }

  /** 스펙 §v2의 "발생 가능한 에러"를 엔드포인트별로 실제 트리거해 스니펫으로 생성하고 status·error.code를 단정한다. */
  @Test
  void generatesV2ErrorSnippets() throws Exception {
    long owner = 100L;
    long stranger = 101L;
    String ownerToken = jwtTokenService.issueAccessToken(owner);
    String strangerToken = jwtTokenService.issueAccessToken(stranger);
    String freshToken = jwtTokenService.issueAccessToken(102L); // 진행 중 세션이 없는 사용자
    String expiredToken = expiredAccessToken(jwtProperties);

    long ownedId = createCompletedDiagnosis(ownerToken);
    long missingId = 9_999_999L;

    // ===== POST /api/v2/diagnoses/start =====
    // #181로 v2가 permitAll이 되면서 401 UNAUTHENTICATED(누락·위조) 세 케이스(start·next·추천)는
    // 도달 불가가 됐다 — guestFlowWithoutToken이 그 자리를 2xx 계약으로 대신 고정한다.
    // 만료 토큰만 401로 남는다(게스트 강등 금지 — 결정 11).
    perform(
        post("/api/v2/diagnoses/start").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "diagnosis-v2-start-token-expired",
        V2_START_SUMMARY,
        V2_START_DESCRIPTION,
        V2_START_401);

    // ===== POST /api/v2/diagnoses/next =====
    // 진행 중 세션 없이 /next — 서버가 임의로 흐름을 되살리지 않는다. 클라이언트가 /start로 복구한다.
    perform(
        post("/api/v2/diagnoses/next")
            .header(HttpHeaders.AUTHORIZATION, bearer(freshToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(answerJson("region", "SEOUL")),
        status().isBadRequest(),
        "DIAGNOSIS_SESSION_NOT_FOUND",
        "diagnosis-v2-next-session-not-found",
        V2_NEXT_SUMMARY,
        V2_NEXT_DESCRIPTION,
        V2_NEXT_400);

    // 현재 문항(pendingField=region)이 아닌 답 — 정본 슬롯 문항과 예외질문이 같은 규칙으로 검증된다.
    start(ownerToken);
    perform(
        post("/api/v2/diagnoses/next")
            .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(answerJson("arcStatus", "ARC_ISSUED")),
        status().isBadRequest(),
        "INVALID_INPUT",
        "diagnosis-v2-next-invalid-input",
        V2_NEXT_SUMMARY,
        V2_NEXT_DESCRIPTION,
        V2_NEXT_400);

    // v1과 달리 본문을 뺄 수 없다 — DiagnosisV2Controller.next 의 @RequestBody(required = false) 때문에
    // 빈 본문은 예외 없이 request=null 로 들어가 다른 코드가 된다. 깨진 JSON 이 있어야 MALFORMED_REQUEST 다.
    perform(
        post("/api/v2/diagnoses/next")
            .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(MALFORMED_BODY),
        status().isBadRequest(),
        "MALFORMED_REQUEST",
        "diagnosis-v2-next-malformed",
        V2_NEXT_SUMMARY,
        V2_NEXT_DESCRIPTION,
        V2_NEXT_400);

    // 401 은 시큐리티 필터(JwtAuthenticationFilter)가 DispatcherServlet 이전에 낸다 — 본문과 무관하므로
    // Swagger 요청 예시 중복을 없애려 본문을 싣지 않는다(#151-4).
    perform(
        post("/api/v2/diagnoses/next")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken))
            .contentType(MediaType.APPLICATION_JSON),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "diagnosis-v2-next-token-expired",
        V2_NEXT_SUMMARY,
        V2_NEXT_DESCRIPTION,
        V2_NEXT_401);

    // ===== GET /api/v2/diagnoses/{id}/recommendations =====
    performWithPathParams(
        get("/api/v2/diagnoses/{diagnosisId}/recommendations", ownedId)
            .header(HttpHeaders.AUTHORIZATION, bearer(strangerToken)),
        status().isForbidden(),
        "FORBIDDEN",
        "diagnosis-v2-recommendations-forbidden",
        V2_RECOMMENDATIONS_SUMMARY,
        V2_RECOMMENDATIONS_DESCRIPTION,
        v2DiagnosisIdPathParameters(),
        V2_RECOMMENDATIONS_403);

    performWithPathParams(
        get("/api/v2/diagnoses/{diagnosisId}/recommendations", missingId)
            .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)),
        status().isNotFound(),
        "DIAGNOSIS_NOT_FOUND",
        "diagnosis-v2-recommendations-not-found",
        V2_RECOMMENDATIONS_SUMMARY,
        V2_RECOMMENDATIONS_DESCRIPTION,
        v2DiagnosisIdPathParameters(),
        V2_RECOMMENDATIONS_404);

    performWithPathParams(
        get("/api/v2/diagnoses/{diagnosisId}/recommendations", ownedId)
            .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
            .param("sort", "unknownKey,desc"),
        status().isBadRequest(),
        "INVALID_INPUT",
        "diagnosis-v2-recommendations-invalid-input",
        V2_RECOMMENDATIONS_SUMMARY,
        V2_RECOMMENDATIONS_DESCRIPTION,
        v2DiagnosisIdPathParameters(),
        V2_RECOMMENDATIONS_400);

    performWithPathParams(
        get("/api/v2/diagnoses/{diagnosisId}/recommendations", ownedId)
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "diagnosis-v2-recommendations-token-expired",
        V2_RECOMMENDATIONS_SUMMARY,
        V2_RECOMMENDATIONS_DESCRIPTION,
        v2DiagnosisIdPathParameters(),
        V2_RECOMMENDATIONS_401);
  }

  /**
   * 게이트 해제 계약(#181) — {@code Authorization} 헤더 <b>없이</b> v2 세 엔드포인트가 모두 2xx다.
   *
   * <p>표시 언어가 {@code en}인 것만 보면 부족하다 — {@code getLanguage}를 부르고 예외를 삼키는 구현도 통과한다. 게스트는 {@code
   * users} 행이 없어 호출 자체가 404이므로 <b>한 번도 부르지 않음</b>을 함께 못 박는다(회원 스텁은 ko라 en 응답이 곧 미호출의 방증이기도 하다).
   */
  @Test
  void guestFlowWithoutToken() throws Exception {
    given(listingRecommendationService.recommendByCriteria(any())).willReturn(pageOf(SAMPLE_VIEW));

    // ① /start — 키 발급 지점. 회원 응답에는 없는 guestSessionId가 실린다(NON_NULL).
    String startBody =
        mockMvc
            .perform(post("/api/v2/diagnoses/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.resultCode").value("NEXT_QUESTION"))
            .andExpect(jsonPath("$.data.question.field").value("region"))
            .andExpect(jsonPath("$.data.question.question").value("Which region will you live in?"))
            .andExpect(jsonPath("$.data.guestSessionId").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String guestKey = read(startBody, "data", "guestSessionId");
    assertThat(guestKey).startsWith("anonymous");

    // ② /next — 헤더 에코로 세션이 이어진다.
    guestNext(guestKey, answerJson("region", "SEOUL"))
        .andExpect(jsonPath("$.data.question.field").value("purpose"));

    // ③ 확정까지 진행 → 추천 조회도 헤더만으로 통과한다(① 지역은 위에서 이미 답했다).
    long diagnosisId = completeGuestFlowAfterRegion(guestKey);
    mockMvc
        .perform(
            get("/api/v2/diagnoses/{diagnosisId}/recommendations", diagnosisId)
                .header(GUEST_SESSION_HEADER, guestKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resultCode").value("MATCHED"))
        .andExpect(jsonPath("$.data.content[0].listingId").value("6858e2000000000000000001"));

    verify(userAccountService, never()).getLanguage(anyLong());
  }

  /** 위조·형식 오류 토큰은 (만료와 달리) 게스트로 통과한다 — 신원 없는 요청과 같은 취급이다(#181). */
  @Test
  void forgedTokenIsTreatedAsGuest() throws Exception {
    mockMvc
        .perform(
            post("/api/v2/diagnoses/start").header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.guestSessionId").exists())
        .andExpect(jsonPath("$.data.question.question").value("Which region will you live in?"));

    verify(userAccountService, never()).getLanguage(anyLong());
  }

  /**
   * 세션 연속성 — 게스트의 {@code /next}는 세션 키가 유일한 조회 키다. 키를 빠뜨렸거나 남의 키면 조회가 비어 돌아와 같은 코드({@code 400
   * DIAGNOSIS_SESSION_NOT_FOUND})가 되고, <b>남의 세션에는 닿지 않는다</b>.
   */
  @Test
  void guestSessionKeyIsRequiredAndScoped() throws Exception {
    given(listingRecommendationService.recommendByCriteria(any())).willReturn(pageOf(SAMPLE_VIEW));
    String keyA = guestStart();
    String keyB = guestStart();

    // 키 없이 /next → 400. 서버가 임의의 세션을 골라 주지 않는다.
    mockMvc
        .perform(
            post("/api/v2/diagnoses/next")
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("region", "SEOUL")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("DIAGNOSIS_SESSION_NOT_FOUND"));

    // A가 진행해도 B 세션은 그대로다 — 두 키가 서로의 세션을 건드리지 않는다.
    guestNext(keyA, answerJson("region", "SEOUL"))
        .andExpect(jsonPath("$.data.question.field").value("purpose"));
    guestNext(keyB, answerJson("region", "SEOUL"))
        .andExpect(jsonPath("$.data.question.field").value("purpose"));

    // 존재하지 않는 키 → 400(남의 세션으로 폴백하지 않는다).
    mockMvc
        .perform(
            post("/api/v2/diagnoses/next")
                .header(GUEST_SESSION_HEADER, "anonymous-does-not-exist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("purpose", "STUDY")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("DIAGNOSIS_SESSION_NOT_FOUND"));
  }

  /**
   * IDOR — 소유권은 신원 <b>종류</b>가 같고 값이 같을 때만 통과한다. 세 방향 모두 막힌다: 게스트 A↛게스트 B · 게스트↛회원 · 회원↛게스트. 진단 id가
   * 전역 순차 채번이라 열거가 쉬워 이 검사가 유일한 방어선이다.
   */
  @Test
  void recommendationsRejectCrossIdentityAccess() throws Exception {
    given(listingRecommendationService.recommendByCriteria(any())).willReturn(pageOf(SAMPLE_VIEW));
    String memberToken = jwtTokenService.issueAccessToken(500L);
    long memberDiagnosisId = createCompletedDiagnosis(memberToken);

    String keyA = guestStart();
    long guestDiagnosisId = completeGuestFlow(keyA);
    String keyB = guestStart();

    // 게스트 A ↛ 게스트 B
    mockMvc
        .perform(
            get("/api/v2/diagnoses/{diagnosisId}/recommendations", guestDiagnosisId)
                .header(GUEST_SESSION_HEADER, keyB))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

    // 게스트 ↛ 회원
    mockMvc
        .perform(
            get("/api/v2/diagnoses/{diagnosisId}/recommendations", memberDiagnosisId)
                .header(GUEST_SESSION_HEADER, keyA))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

    // 회원 ↛ 게스트 — 회원 요청은 헤더가 실려 와도 무시한다(키를 훔쳐도 통하지 않는다).
    mockMvc
        .perform(
            get("/api/v2/diagnoses/{diagnosisId}/recommendations", guestDiagnosisId)
                .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                .header(GUEST_SESSION_HEADER, keyA))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

    // 신원 없는 요청(토큰도 키도 없음)은 어떤 진단도 소유하지 않는다.
    mockMvc
        .perform(get("/api/v2/diagnoses/{diagnosisId}/recommendations", guestDiagnosisId))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

    // 회원 무회귀 — 본인 진단은 그대로 조회된다.
    mockMvc
        .perform(
            get("/api/v2/diagnoses/{diagnosisId}/recommendations", memberDiagnosisId)
                .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resultCode").value("MATCHED"));
  }

  /**
   * 결정 3의 가드 — <b>v1 진단은 게스트에게 열리지 않는다</b>. {@code permitAll} 매처는 {@code /api/v2/diagnoses/**}
   * 하나뿐이고 v1 7개는 {@code anyRequest().authenticated()}에 남아 토큰이 필수다. v1에 매처가 조용히 추가되면 여기서 깨진다.
   */
  @Test
  void v1DiagnosesStayMemberOnly() throws Exception {
    mockMvc
        .perform(get("/api/v1/diagnoses"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

    mockMvc
        .perform(post("/api/v1/diagnoses").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

    mockMvc
        .perform(get("/api/v1/diagnoses/{diagnosisId}", 1L))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  /** 게스트 경로의 만료 토큰은 200이 아니라 401 {@code TOKEN_EXPIRED}다 — 조용한 게스트 강등을 막는다(결정 11). */
  @Test
  void expiredTokenIsNotDowngradedToGuest() throws Exception {
    mockMvc
        .perform(
            post("/api/v2/diagnoses/start")
                .header(HttpHeaders.AUTHORIZATION, bearer(expiredAccessToken(jwtProperties))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  // ---- helpers (guest) ----

  /** 토큰 없이 {@code /start}를 호출해 발급된 게스트 세션 키를 돌려준다. */
  private String guestStart() throws Exception {
    String body =
        mockMvc
            .perform(post("/api/v2/diagnoses/start"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return read(body, "data", "guestSessionId");
  }

  private ResultActions guestNext(String guestKey, String body) throws Exception {
    return mockMvc
        .perform(
            post("/api/v2/diagnoses/next")
                .header(GUEST_SESSION_HEADER, guestKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  /** 이미 {@code /start}한 게스트 세션을 ① 지역부터 끝까지 진행해 확정된 diagnosisId를 돌려준다(STUDY 흐름). */
  private long completeGuestFlow(String guestKey) throws Exception {
    guestNext(guestKey, answerJson("region", "SEOUL"));
    return completeGuestFlowAfterRegion(guestKey);
  }

  /**
   * ① 지역까지 답해 둔 게스트 세션을 ② 목적부터 끝까지 진행한다(지역을 두 번 답하면 INVALID_INPUT이다).
   *
   * <p>마지막 {@code /next}(⑥ {@code arcStatus})가 게스트 흐름의 확정 지점이라 여기서 {@code
   * diagnosis-v2-next-completed-guest} 스니펫을 낸다 — 회원 확정({@code diagnosis-v2-next-completed})만 문서화하면
   * 게스트가 {@code X-Guest-Session-Id}만으로 확정에 도달하는 모습이 명세에 없다.
   *
   * <p><b>이 헬퍼는 여러 테스트가 호출해 한 실행에서 스니펫이 여러 번 덮어써진다 — 무해하다.</b> 모든 호출부가 ① 지역(`SEOUL`)까지 답한 같은 상태로
   * 진입하고 헬퍼가 늘 같은 답을 같은 순서로 보내므로 요청·응답의 모양이 동일하다(달라지는 것은 {@code diagnosisId}와 게스트 세션 키 값뿐이다). 그래서
   * 스니펫을 밖으로 꺼내지 않는다.
   */
  private long completeGuestFlowAfterRegion(String guestKey) throws Exception {
    guestNext(guestKey, answerJson("purpose", "STUDY"));
    guestNext(guestKey, answerJson("university", "SNU_CAU_SOONGSIL"));
    guestNext(guestKey, "{\"field\":\"conditions\",\"codes\":[\"FEMALE_ONLY\"]}");
    guestNext(guestKey, answerRentJson(200000, 500000));
    String completed =
        guestNext(guestKey, answerJson("arcStatus", "ARC_ISSUED"))
            .andExpect(jsonPath("$.data.resultCode").value("COMPLETED"))
            .andExpect(jsonPath("$.data.question").doesNotExist())
            .andDo(
                document(
                    "diagnosis-v2-next-completed-guest",
                    resourceDetails()
                        .tag(ApiDocsTags.DIAGNOSIS)
                        .summary(V2_NEXT_SUMMARY)
                        .description(V2_NEXT_DESCRIPTION),
                    requestHeaders(guestSessionHeader()),
                    requestFields(nextRequestFields()),
                    responseFields(nextResponseFields())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return Long.parseLong(read(completed, "data", "diagnosisId"));
  }

  // ---- helpers (flow) ----

  private void start(String token) throws Exception {
    mockMvc
        .perform(post("/api/v2/diagnoses/start").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk());
  }

  private ResultActions next(String token, String body) throws Exception {
    return mockMvc
        .perform(
            post("/api/v2/diagnoses/next")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  /** v2 흐름만으로 한 사용자의 진단을 확정까지 진행하고 발급된 diagnosisId를 돌려준다(STUDY 흐름). */
  private long createCompletedDiagnosis(String token) throws Exception {
    start(token);
    next(token, answerJson("region", "SEOUL"));
    next(token, answerJson("purpose", "STUDY"));
    next(token, answerJson("university", "SNU_CAU_SOONGSIL"));
    next(token, "{\"field\":\"conditions\",\"codes\":[\"FEMALE_ONLY\"]}");
    next(token, answerRentJson(200000, 500000));
    String completed =
        next(token, answerJson("arcStatus", "ARC_ISSUED"))
            .andExpect(jsonPath("$.data.resultCode").value("COMPLETED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return Long.parseLong(read(completed, "data", "diagnosisId"));
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

  /** {@code POST /next} 요청 바디 — v1 §2 AnswerRequest와 같은 구조(해당하는 쪽만 채우고 나머지는 보내지 않는다). */
  private static List<FieldDescriptor> nextRequestFields() {
    return List.of(
        codeField(
            "field",
            QUESTION_FIELD_CODES,
            "현재 문항의 제출 필드명 — 서버가 직전에 낸 `question.field`와 같아야 한다(다르면 `INVALID_INPUT`)"),
        optField(
            "code",
            JsonFieldType.STRING,
            "단일 선택(`SINGLE`) 답의 코드. 선택지 `options[].code` 중 하나이며,"
                + " ① 지역 0건 예외질문(`regionRetry`)은 `YES` 또는 `NO`다"),
        optCodeArrayField(
            "codes",
            SELECTABLE_CONDITION_CODES,
            "다중 선택(`MULTI`) 답의 코드 집합 — ④ `conditions` 전용이며 최대 3개·중복 불가."
                + " 파생 조건 `NO_ARC`는 ⑥에서 서버가 만들므로 여기서 고를 수 없다"),
        optField("min", JsonFieldType.NUMBER, "⑤ `monthlyRent` 월세 하한(KRW 정수, 0 이상이고 `max` 이하)"),
        optField("max", JsonFieldType.NUMBER, "⑤ `monthlyRent` 월세 상한(KRW 정수, `min` 이상)"));
  }

  /**
   * {@code POST /start} 200 응답. 회원·게스트 두 스니펫이 같은 헬퍼를 쓴다 — 다른 점은 {@code guestSessionId} 하나뿐이고, 그 필드는
   * 회원 응답에서 <b>값이 null이 아니라 키 자체가 생략</b>된다({@code NON_NULL}).
   */
  private static List<FieldDescriptor> startResponseFields() {
    List<FieldDescriptor> fields = new ArrayList<>();
    fields.add(field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"));
    fields.add(
        codeField(
            "data.resultCode",
            START_RESULT_CODES,
            "결과코드 — `/start`는 언제나 `NEXT_QUESTION`이다(다른 코드로 갈 수 없다)"));
    fields.addAll(questionFields(false));
    fields.add(
        optField(
            "data.guestSessionId",
            JsonFieldType.STRING,
            "게스트 세션 키(`anonymous<uuid>`) — 비회원 `/start` 응답에만 실린다."
                + " 회원 응답에서는 값이 null이 아니라 **필드 자체가 생략**된다."
                + " 클라이언트는 이 값을 보관했다가 이후 `/next`·추천 조회에 `X-Guest-Session-Id` 헤더로 에코한다"));
    fields.add(errorNull());
    return List.copyOf(fields);
  }

  /**
   * {@code POST /next} 200 응답. {@code resultCode} 네 갈래의 스니펫이 <b>모두 이 헬퍼 하나</b>를 쓴다 — 같은 {@code
   * (path, method, status)}라 기술자가 어차피 하나로 접히므로 여러 벌을 두면 승자가 파일 순회 순서에 좌우된다. 갈래마다 사라지는 payload는
   * {@code optional}이고, 각 스니펫이 {@code doesNotExist()}로 「이 케이스에는 없다」를 못 박는다.
   */
  private static List<FieldDescriptor> nextResponseFields() {
    List<FieldDescriptor> fields = new ArrayList<>();
    fields.add(field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"));
    fields.add(
        enumField(
            "data.resultCode",
            FlowResultCode.class,
            "다음에 할 일을 알리는 결과코드. 값에 따라 아래 payload 중 하나만 실린다"));
    fields.addAll(questionFields(true));
    fields.add(
        optField(
            "data.diagnosisId",
            JsonFieldType.NUMBER,
            "확정된 진단 식별자 — `resultCode=COMPLETED`일 때만 실린다."
                + " 그 외 결과코드에서는 값이 null이 아니라 **필드 자체가 생략**된다."
                + " 이 id로 `GET /api/v2/diagnoses/{diagnosisId}/recommendations`를 별도 호출한다"));
    fields.add(errorNull());
    return List.copyOf(fields);
  }

  /**
   * 흐름 응답의 {@code question} payload(v1 §1 {@code QuestionResponse}와 같은 형태).
   *
   * @param optional {@code true}면 {@code NEXT_QUESTION}이 아닌 갈래에서 통째로 생략되는 {@code /next}용
   */
  private static List<FieldDescriptor> questionFields(boolean optional) {
    List<FieldDescriptor> fields = new ArrayList<>();
    fields.add(
        describe(
            optional,
            "data.question",
            JsonFieldType.OBJECT,
            "다음 문항 1개 — `resultCode=NEXT_QUESTION`일 때 실린다."
                + " `/next`의 다른 결과코드(`COMPLETED`·`RESTART`·`TERMINATED`)에서는"
                + " 값이 null이 아니라 **필드 자체가 생략**된다(`/start`는 언제나 이 필드가 있다)"));
    fields.add(
        describe(
            optional, "data.question.step", JsonFieldType.NUMBER, "문항의 단계 번호(1~6). 정본 순서에서 파생된다"));
    fields.add(
        codeFieldMaybeOptional(
            optional,
            "data.question.field",
            QUESTION_FIELD_CODES,
            "제출 필드명 — 다음 `/next` 요청의 `field`에 그대로 싣는다."
                + " `regionRetry`는 ① 지역 0건 예외질문이며 `YES`/`NO`로 답한다"));
    fields.add(
        describe(
            optional,
            "data.question.question",
            JsonFieldType.STRING,
            "사용자 표시 언어로 번역된 질문 문구(미지원 언어는 영어 폴백, 게스트는 영어)"));
    fields.add(
        codeFieldMaybeOptional(
            optional,
            "data.question.select.type",
            SELECT_TYPE_CODES,
            "선택 방식 — `SINGLE`은 1택, `MULTI`는 다중, `NUMBER_RANGE`는 선택지가 아니라 `min`·`max` 숫자 2개 입력"));
    fields.add(
        describe(
            optional,
            "data.question.select.max",
            JsonFieldType.NUMBER,
            "최대 선택 개수 — ④ `MULTI`는 3, 그 외는 1"));
    fields.add(
        describe(
            optional,
            "data.question.options",
            JsonFieldType.ARRAY,
            "선택지 목록. ⑤ `monthlyRent`(`NUMBER_RANGE`)만 빈 배열이며 클라이언트가 숫자 입력 2개를 그린다"));
    fields.add(
        optField(
            "data.question.options[].code",
            JsonFieldType.STRING,
            "선택지 코드 — 언어와 무관하게 같고 확정 검증 enum과 1:1이다." + " `NUMBER_RANGE` 문항은 배열이 비어 원소가 없다"));
    fields.add(optField("data.question.options[].label", JsonFieldType.STRING, "번역된 선택지 표시 라벨"));
    return fields;
  }

  private static FieldDescriptor describe(
      boolean optional, String path, JsonFieldType type, String description) {
    return optional ? optField(path, type, description) : field(path, type, description);
  }

  private static FieldDescriptor codeFieldMaybeOptional(
      boolean optional, String path, List<String> allowedValues, String description) {
    return optional
        ? optCodeField(path, allowedValues, description)
        : codeField(path, allowedValues, description);
  }

  /**
   * {@code GET /{id}/recommendations} 200 응답. MATCHED·NO_MATCH 두 스니펫이 같은 헬퍼를 쓴다 — 0건이면 배열이 비어 원소가
   * 없으므로 원소 필드는 {@code optional}이고, 각 스니펫이 값 단정/{@code doesNotExist()}로 계약을 되메운다.
   */
  private static List<FieldDescriptor> v2RecommendationFields() {
    List<FieldDescriptor> fields = new ArrayList<>();
    fields.add(field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"));
    fields.add(
        enumField(
            "data.resultCode",
            RecommendationResultCode.class,
            "매칭 결과 — `MATCHED`는 매물 있음, `NO_MATCH`는 0건(에러가 아니며 v1과 달리 조정 제안이 없다)"));
    fields.addAll(recommendationContentFields());
    fields.addAll(pageFields());
    fields.add(errorNull());
    return List.copyOf(fields);
  }

  /**
   * 게스트 세션 키 에코 요청 헤더 서술자({@code /next}·추천 조회 공용).
   *
   * <p><b>{@code optional()}이 필수다</b> — {@code HeaderDescriptorWithType.fromHeaderDescriptor}가
   * {@code required = !optional}로 옮기므로, 빠뜨리면 회원도 반드시 보내야 하는 헤더로 문서화된다(#151 실측 버그).
   */
  private static HeaderDescriptor guestSessionHeader() {
    return headerWithName(GUEST_SESSION_HEADER)
        .optional()
        .description(
            "게스트 세션 키(`anonymous<uuid>`) — `/start` 응답의 `guestSessionId`를 그대로 실어 보낸다."
                + " **회원은 보내지 않는다**(`Authorization` 토큰으로 식별하며 실려 와도 무시한다)."
                + " 게스트가 빠뜨렸거나 남의 키면 세션을 못 찾아 `400 DIAGNOSIS_SESSION_NOT_FOUND`다");
  }

  private static ParameterDescriptor[] v2DiagnosisIdPathParameters() {
    return new ParameterDescriptor[] {
      parameterWithName("diagnosisId")
          .description("`resultCode=COMPLETED` 응답으로 받은 확정 진단 식별자(본인 신원 소유)")
    };
  }

  // ---- seed / fixtures ----

  /**
   * v2 흐름이 지나는 문항 전부를 시드한다 — 정본 6슬롯(③은 university·district 2건)과 ① 지역 0건 예외질문(regionRetry). 흐름이 자동으로
   * 다음 문항을 내므로 하나라도 빠지면 카탈로그 조회에서 깨진다(v1 스니펫 테스트는 클라가 지정한 step만 조회해 일부만 시드해도 됐다).
   *
   * <p>선택지는 운영 카탈로그보다 짧은 축약 시드다 — 문서에는 실제 허용 코드를 스키마 enum으로 싣고 예시가 시드임을 description에 밝힌다.
   */
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
            "regionRetry",
            "SINGLE",
            1,
            Map.of(
                "en",
                    "There are no rooms in this region yet. Would you like to look in another region?",
                "ko", "현재 지역에는 매물이 없어요. 다른 지역 방을 찾아보시겠어요?"),
            List.of(option("YES", "Yes", "예"), option("NO", "No", "아니오"))));
    questionMongoRepository.save(
        question(
            "purpose",
            "SINGLE",
            1,
            Map.of("en", "What is your purpose of stay?", "ko", "입국 목적이 무엇인가요?"),
            List.of(option("STUDY", "Study", "유학(학업)"), option("NON_STUDY", "Other", "그 외"))));
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
    questionMongoRepository.save(
        question(
            "conditions",
            "MULTI",
            3,
            Map.of(
                "en", "Select your housing conditions (up to 3).",
                "ko", "원하는 주거 조건을 선택하세요(최대 3개)."),
            List.of(
                option("FEMALE_ONLY", "Female only", "여성 전용"),
                option("PRIVATE_BATH", "Private bath", "개인 욕실"),
                option("ENGLISH_OK", "English available", "영어 가능"))));
    questionMongoRepository.save(
        question(
            "monthlyRent",
            "NUMBER_RANGE",
            1,
            Map.of(
                "en", "What is your monthly rent range (min–max)? (KRW)",
                "ko", "월세 범위(최소~최대)는 얼마인가요?(원)"),
            List.of()));
    questionMongoRepository.save(
        question(
            "arcStatus",
            "SINGLE",
            1,
            Map.of("en", "What is your ARC issuance status?", "ko", "외국인등록증(ARC) 발급 상태는 어떤가요?"),
            List.of(
                option("ARC_ISSUED", "Issued", "발급 완료"), option("NO_ARC", "Not issued", "미발급"))));
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

  private static final RecommendedListingView SAMPLE_VIEW =
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
              new ListingCodeLabelView("PRIVATE_BATH", "Private Bath")));

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
