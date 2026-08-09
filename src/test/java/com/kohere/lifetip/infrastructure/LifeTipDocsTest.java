package com.kohere.lifetip.infrastructure;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails;
import static com.kohere.docs.ApiDocsErrors.errorSnippet;
import static com.kohere.docs.ApiDocsFields.codeField;
import static com.kohere.docs.ApiDocsFields.errorNull;
import static com.kohere.docs.ApiDocsFields.field;
import static com.kohere.docs.ApiDocsFields.optField;
import static com.kohere.docs.ApiDocsParams.codeParam;
import static com.kohere.docs.DocsTokens.bearer;
import static com.kohere.docs.DocsTokens.expiredAccessToken;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kohere.TestcontainersConfiguration;
import com.kohere.common.security.JwtProperties;
import com.kohere.common.security.JwtTokenService;
import com.kohere.docs.ApiDocsTags;
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
 * 생활 팁 REST Docs 스니펫 생성 테스트(ADR-0007·0016). 성공(주제 목록·주제별 팁)과 스펙(08-life-tips.md) 에러(404·401)를 실제
 * HTTP 스택으로 생성한다. 표시 언어(user getLanguage)는 {@code @MockitoBean}, MongoDB는 실제 컨테이너로 구동하며 시더는 test
 * 프로파일에서 비활성이라 이 테스트가 직접 시드한다.
 *
 * <p><b>#181로 인가 계약이 바뀌었다</b> — 생활 팁은 {@code permitAll}이라 토큰 없이도 2xx이고 세입자 게이트가 없다. 401
 * UNAUTHENTICATED·403 AUTH_ONBOARDING_REQUIRED 스니펫은 도달 불가라 제거했고, 그 자리를 {@link
 * #guestAccessWithoutToken}·{@link #onboardingTokenIsTreatedAsMemberNotGuest}·{@link
 * #landlordGetsKoreanLabels}가 2xx 계약으로 고정한다. 401은 <b>만료 토큰</b>에만 남는다.
 *
 * <p>문서 문구는 오퍼레이션(path+method)당 상수 1벌({@code LIFE_TIPS_TOPICS_*}·{@code LIFE_TIPS_TIPS_*})을 성공·에러
 * 스니펫이 공유한다(#151) — 생성기가 같은 오퍼레이션의 summary/description 중 첫 non-blank 하나만 채택하므로, 예전처럼 에러 스니펫이 제 문구를
 * 따로 들고 있으면 그게 성공 오퍼레이션의 제목·설명을 차지한다.
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
@Testcontainers
@Import(TestcontainersConfiguration.class)
class LifeTipDocsTest {

  @Container @ServiceConnection static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

  // 서명이 깨진 토큰. #181 이후 위조 토큰은 401이 아니라 신원 없는 요청과 같은 게스트 취급이라 200이다
  // (guestAccessWithoutToken이 그 계약을 고정한다). Bearer JWT 구조라 Swagger 보안 스킴은 유지된다.
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

  /** 캐노니컬 카탈로그 시드({@link LifeTipCatalogSeedChangeUnit})가 적재하는 주제 코드 전체. enum 클래스가 없는 DB 카탈로그다. */
  private static final List<String> TOPIC_CODES =
      List.of("MOVING_IN", "ADMINISTRATION", "TRANSPORT", "FINANCE", "HOUSING");

  // ===== GET /api/v1/life-tips/topics =====

  private static final String LIFE_TIPS_TOPICS_SUMMARY = "생활 팁 주제 목록 조회";

  private static final String LIFE_TIPS_TOPICS_DESCRIPTION =
      """
      생활 팁 주제 전체를 노출 순서대로 반환한다. 고정·소규모 카탈로그라 페이지 객체 없이 배열 하나만 담긴다.

      **인증**

      - `Authorization` 헤더는 선택이다. 헤더가 없거나 서명이 깨진 토큰이면 게스트로 처리해 200을 반환한다.
      - 역할 게이트가 없다 — 게스트·세입자·임대인 모두 200이며 응답 스키마도 동일하다.
      - 만료된 access token만 401 `TOKEN_EXPIRED`다. 게스트로 강등하지 않고 재발급을 유도한다.

      **표시 언어와 필드**

      - 게스트: `en` 고정 — 서버가 `getLanguage`를 호출하지 않는다.
      - 세입자: 본인이 고른 `users.lang`. 미선택이면 `en`으로 폴백한다(온보딩 미완료 회원도 여기 해당).
      - 임대인: `ko` 고정 — 임대인 온보딩에서 서버가 `lang='ko'`로 확정한다.
      - 번역 대상은 `name`·`shortDescription`·`longDescription`뿐이다. `code`와 이미지 2필드는 언어와 무관하다.
      - 홈 카드는 `imageUrl` + `shortDescription`, 주제 상세 상단은 `backgroundImageUrl` + `longDescription`으로 그린다.
      - 주제가 0건이면 `topics: []`다(에러 아님).
      - `topics[].code`는 `GET /api/v1/life-tips/topics/{topicCode}/tips`의 경로 값으로 그대로 쓴다.

      **에러 코드**

      - 401 `TOKEN_EXPIRED` — 만료된 access token으로 호출
      """;

  private static final String[] LIFE_TIPS_TOPICS_401 = {"TOKEN_EXPIRED"};

  // ===== GET /api/v1/life-tips/topics/{topicCode}/tips =====

  private static final String LIFE_TIPS_TIPS_SUMMARY = "주제별 생활 팁 목록 조회";

  private static final String LIFE_TIPS_TIPS_DESCRIPTION =
      """
      고른 주제에 속한 생활 팁(제목·내용·사진) 전체를 노출 순서대로 반환한다. 페이지 객체 없이 배열 하나만 담긴다.

      **인증**

      - `Authorization` 헤더는 선택이다. 헤더가 없거나 서명이 깨진 토큰이면 게스트로 처리해 200을 반환한다.
      - 역할 게이트가 없다 — 게스트·세입자·임대인 모두 200이며 응답 스키마도 동일하다.
      - 만료된 access token만 401 `TOKEN_EXPIRED`다. 게스트로 강등하지 않고 재발급을 유도한다.

      **표시 언어와 필드**

      - 게스트: `en` 고정 — 서버가 `getLanguage`를 호출하지 않는다.
      - 세입자: 본인이 고른 `users.lang`. 미선택이면 `en`으로 폴백한다(온보딩 미완료 회원도 여기 해당).
      - 임대인: `ko` 고정.
      - 번역 대상은 `title`·`content`뿐이다. `id`와 `imageUrl`은 언어와 무관하다.
      - 사진이 없는 팁은 `imageUrl`이 `null`이다(필드는 남는다).
      - 주제는 있으나 팁이 0건이면 `tips: []`다(에러 아님).

      **에러 코드**

      - 401 `TOKEN_EXPIRED` — 만료된 access token으로 호출
      - 404 `LIFE_TIP_TOPIC_NOT_FOUND` — 경로의 `topicCode`가 카탈로그에 없음
      """;

  private static final String[] LIFE_TIPS_TIPS_401 = {"TOKEN_EXPIRED"};
  private static final String[] LIFE_TIPS_TIPS_404 = {"LIFE_TIP_TOPIC_NOT_FOUND"};

  @Autowired private WebApplicationContext context;
  @Autowired private JwtTokenService jwtTokenService;
  @Autowired private JwtProperties jwtProperties;
  @Autowired private LifeTipTopicMongoRepository topicRepository;
  @Autowired private LifeTipMongoRepository tipRepository;

  @MockitoBean private UserAccountService userAccountService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp(RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .apply(documentationConfiguration(restDocumentation))
            .build();
    topicRepository.deleteAll();
    tipRepository.deleteAll();
    seed();
    // 표시 언어는 user 공개 query로 취득 — 사용자가 고른 ko(users.lang)를 가정해 한국어 라벨을 내려받는다.
    // userType은 더 이상 조회하지 않는다(#181로 세입자 게이트 제거) — 스텁을 두면 죽은 코드다.
    given(userAccountService.getLanguage(anyLong())).willReturn("ko");
  }

  @Test
  void generatesLifeTipSnippets() throws Exception {
    String token = jwtTokenService.issueAccessToken(1L);

    mockMvc
        .perform(get("/api/v1/life-tips/topics").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.topics[0].code").value("MOVING_IN"))
        .andExpect(jsonPath("$.data.topics[0].name").value("입주·이사"))
        .andExpect(jsonPath("$.data.topics[0].shortDescription").value("한국 생활 시작에 필요한 기본 정보."))
        .andExpect(
            jsonPath("$.data.topics[0].imageUrl")
                .value("https://cdn.kohere.app/life-tips/topics/moving-in/card.png"))
        .andDo(
            document(
                "life-tips-topics",
                resourceDetails()
                    .tag(ApiDocsTags.LIFE_TIPS)
                    .summary(LIFE_TIPS_TOPICS_SUMMARY)
                    .description(LIFE_TIPS_TOPICS_DESCRIPTION),
                responseFields(topicsResponseFields())));

    mockMvc
        .perform(
            get("/api/v1/life-tips/topics/{topicCode}/tips", "MOVING_IN")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.tips[0].title").value("전입신고"))
        .andDo(
            document(
                "life-tips-topic-tips",
                resourceDetails()
                    .tag(ApiDocsTags.LIFE_TIPS)
                    .summary(LIFE_TIPS_TIPS_SUMMARY)
                    .description(LIFE_TIPS_TIPS_DESCRIPTION),
                pathParameters(tipsPathParameters()),
                responseFields(tipsResponseFields())));
  }

  @Test
  void generatesLifeTipErrorSnippets() throws Exception {
    String token = jwtTokenService.issueAccessToken(200L);

    // 404 — 존재하지 않는 주제
    performTipsError(
        get("/api/v1/life-tips/topics/{topicCode}/tips", "UNKNOWN_TOPIC")
            .header(HttpHeaders.AUTHORIZATION, bearer(token)),
        status().isNotFound(),
        "LIFE_TIP_TOPIC_NOT_FOUND",
        "life-tips-topic-tips-not-found",
        LIFE_TIPS_TIPS_404);

    // #181로 생활 팁이 permitAll이 되면서 401 UNAUTHENTICATED(누락·위조)와 403
    // AUTH_ONBOARDING_REQUIRED는 도달 불가가 됐다 — guestAccessWithoutToken이 그 자리를
    // 2xx 계약으로 대신 고정한다. 401은 만료 토큰에만 남는다.
    performTipsError(
        get("/api/v1/life-tips/topics/{topicCode}/tips", "MOVING_IN")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredAccessToken(jwtProperties))),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "life-tips-topic-tips-token-expired",
        LIFE_TIPS_TIPS_401);

    // 주제 목록은 에러 스니펫이 0건이라 스펙 08이 명시한 401이 Swagger 응답 목록에 아예 없었다(#151-3).
    // 401은 시큐리티 필터 단계에서 끊기므로 요청 본문과 무관하다(GET이라 애초에 없다).
    performTopicsError(
        get("/api/v1/life-tips/topics")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredAccessToken(jwtProperties))),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "life-tips-topics-token-expired",
        LIFE_TIPS_TOPICS_401);
  }

  /**
   * 게이트 해제 계약(#181) — {@code Authorization} 헤더 <b>없이</b> 생활 팁 두 엔드포인트가 2xx다.
   *
   * <p>기본 언어 {@code en}만 확인하면 부족하다 — {@code getLanguage}를 부르고 예외를 삼키는 구현도 통과한다. 게스트는 {@code users}
   * 행이 없어 호출 자체가 404이므로 <b>한 번도 부르지 않음</b>을 함께 못 박는다. 위조 토큰도 신원 없는 요청과 같은 취급이다.
   *
   * <p>앞 두 요청은 스니펫으로도 낸다(#151-3) — 회원 예시({@code life-tips-topics}·{@code life-tips-topic-tips})는 ko
   * 라벨이라 「토큰 없이 호출하면 en으로 온다」는 계약이 Swagger에 전혀 보이지 않았다. 같은 {@code (path, method, 200)}이라 스키마는 회원
   * 예시와 병합되고(그래서 <b>같은 필드 헬퍼</b>를 쓴다) 예시만 identifier 별로 드롭다운에 나란히 남는다. 세 번째 위조 토큰 요청은 두 번째 요청과 같은
   * 게스트 200이라 예시를 늘리지 않고 단정만 유지한다.
   */
  @Test
  void guestAccessWithoutToken() throws Exception {
    mockMvc
        .perform(get("/api/v1/life-tips/topics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.topics[0].code").value("MOVING_IN"))
        .andExpect(jsonPath("$.data.topics[0].name").value("Moving In"))
        .andDo(
            document(
                "life-tips-topics-guest",
                resourceDetails()
                    .tag(ApiDocsTags.LIFE_TIPS)
                    .summary(LIFE_TIPS_TOPICS_SUMMARY)
                    .description(LIFE_TIPS_TOPICS_DESCRIPTION),
                responseFields(topicsResponseFields())));

    mockMvc
        .perform(get("/api/v1/life-tips/topics/{topicCode}/tips", "MOVING_IN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.tips[0].title").value("Resident registration"))
        .andDo(
            document(
                "life-tips-topic-tips-guest",
                resourceDetails()
                    .tag(ApiDocsTags.LIFE_TIPS)
                    .summary(LIFE_TIPS_TIPS_SUMMARY)
                    .description(LIFE_TIPS_TIPS_DESCRIPTION),
                pathParameters(tipsPathParameters()),
                responseFields(tipsResponseFields())));

    mockMvc
        .perform(
            get("/api/v1/life-tips/topics").header(HttpHeaders.AUTHORIZATION, bearer(FORGED_TOKEN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.topics[0].name").value("Moving In"));

    verify(userAccountService, never()).getLanguage(anyLong());
    verify(userAccountService, never()).getUserType(anyLong());
  }

  /**
   * 온보딩 미완료(ROLE_ONBOARDING) 토큰은 게스트가 아니라 회원 취급이다(결정 6) — 200이면서 언어는 {@code users.lang}(ko)이다.
   * {@code userId != null}이므로 {@code getLanguage}를 정상적으로 부른다.
   */
  @Test
  void onboardingTokenIsTreatedAsMemberNotGuest() throws Exception {
    String onboardingToken = jwtTokenService.issueOnboardingToken(201L);

    mockMvc
        .perform(
            get("/api/v1/life-tips/topics")
                .header(HttpHeaders.AUTHORIZATION, bearer(onboardingToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.topics[0].name").value("입주·이사"));
  }

  /**
   * 임대인도 200이며 응답 언어는 본인 {@code users.lang}(ko)이다 — 세입자 게이트가 사라졌음을 HTTP 계약으로 고정한다(#181). 게이트를 되살리면
   * 여기서 403으로 깨진다.
   */
  @Test
  void landlordGetsKoreanLabels() throws Exception {
    String landlordToken = jwtTokenService.issueAccessToken(300L);

    mockMvc
        .perform(
            get("/api/v1/life-tips/topics")
                .header(HttpHeaders.AUTHORIZATION, bearer(landlordToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.topics[0].name").value("입주·이사"));

    verify(userAccountService, never()).getUserType(anyLong());
  }

  // --- field / parameter descriptors ---

  /**
   * {@code codeField}는 {@link FieldDescriptor}라 {@code pathParameters}에 넣을 수 없으므로 허용값을 실으려면 {@code
   * ApiDocsParams.codeParam}을 쓴다. 성공·에러 스니펫이 같은 배열을 쓴다(#151 규약 12).
   */
  private static ParameterDescriptor[] tipsPathParameters() {
    return new ParameterDescriptor[] {
      codeParam(
          "topicCode",
          TOPIC_CODES,
          "주제 코드(UPPER_SNAKE). 목록은 `GET /api/v1/life-tips/topics` 응답의 `topics[].code`로 확인한다")
    };
  }

  private static List<FieldDescriptor> topicsResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        codeField("data.topics[].code", TOPIC_CODES, "언어 무관 주제 식별 코드(주제별 팁 조회의 경로 값)"),
        field("data.topics[].name", JsonFieldType.STRING, "표시 언어로 번역된 주제 표시명"),
        field("data.topics[].shortDescription", JsonFieldType.STRING, "번역된 짧은 설명(홈 카드용)"),
        field("data.topics[].longDescription", JsonFieldType.STRING, "번역된 긴 설명(주제 상세 상단용)"),
        field("data.topics[].imageUrl", JsonFieldType.STRING, "홈 카드 이미지 URL(언어 무관, 항상 존재)"),
        field(
            "data.topics[].backgroundImageUrl",
            JsonFieldType.STRING,
            "주제 상세 상단 배경 이미지 URL(언어 무관, 항상 존재)"),
        errorNull());
  }

  private static List<FieldDescriptor> tipsResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.tips[].id", JsonFieldType.STRING, "팁 식별자(ObjectId hex, 언어 무관)"),
        field("data.tips[].title", JsonFieldType.STRING, "표시 언어로 번역된 제목"),
        field("data.tips[].content", JsonFieldType.STRING, "표시 언어로 번역된 내용"),
        optField("data.tips[].imageUrl", JsonFieldType.STRING, "사진 URL(언어 무관). 사진이 없는 팁은 `null`"),
        errorNull());
  }

  // --- seed / helpers ---

  private void seed() {
    topicRepository.save(
        topicDoc(
            "MOVING_IN",
            1,
            Map.of("en", "Moving In", "ko", "입주·이사"),
            Map.of("en", "The basics for starting life in Korea.", "ko", "한국 생활 시작에 필요한 기본 정보."),
            Map.of("en", "Move-in steps, utilities, and filings.", "ko", "입주 절차·공과금·각종 신고 정리."),
            "https://cdn.kohere.app/life-tips/topics/moving-in/card.png",
            "https://cdn.kohere.app/life-tips/topics/moving-in/background.png"));
    topicRepository.save(
        topicDoc(
            "TRANSPORT",
            2,
            Map.of("en", "Transport", "ko", "교통"),
            Map.of("en", "Getting around Korea.", "ko", "한국에서의 이동 정보."),
            Map.of("en", "Transit cards, subway, bus, taxi.", "ko", "교통카드·지하철·버스·택시."),
            "https://cdn.kohere.app/life-tips/topics/transport/card.png",
            "https://cdn.kohere.app/life-tips/topics/transport/background.png"));
    tipRepository.save(
        tipDoc(
            "MOVING_IN",
            1,
            Map.of("en", "Resident registration", "ko", "전입신고"),
            Map.of("en", "File within 14 days.", "ko", "14일 이내에 신고하세요."),
            "https://cdn.kohere.app/life-tips/moving-in/1.png"));
    tipRepository.save(
        tipDoc(
            "MOVING_IN",
            2,
            Map.of("en", "Utilities", "ko", "공과금 개통"),
            Map.of("en", "Set up electricity, water, gas.", "ko", "전기·수도·가스를 개통하세요."),
            "https://cdn.kohere.app/life-tips/moving-in/2.png"));
  }

  private static LifeTipTopicDocument topicDoc(
      String code,
      int order,
      Map<String, String> name,
      Map<String, String> shortDescription,
      Map<String, String> longDescription,
      String imageUrl,
      String backgroundImageUrl) {
    return LifeTipTopicDocument.builder()
        .id(code)
        .name(name)
        .shortDescription(shortDescription)
        .longDescription(longDescription)
        .imageUrl(imageUrl)
        .backgroundImageUrl(backgroundImageUrl)
        .order(order)
        .build();
  }

  private static LifeTipDocument tipDoc(
      String topicCode,
      int order,
      Map<String, String> title,
      Map<String, String> content,
      String imageUrl) {
    return LifeTipDocument.builder()
        .topicCode(topicCode)
        .order(order)
        .title(title)
        .content(content)
        .imageUrl(imageUrl)
        .build();
  }

  /** 주제 목록은 path 변수가 없어 {@code pathParameters} 없는 {@code errorSnippet} 오버로드를 쓴다. */
  private void performTopicsError(
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
                ApiDocsTags.LIFE_TIPS,
                LIFE_TIPS_TOPICS_SUMMARY,
                LIFE_TIPS_TOPICS_DESCRIPTION,
                errorCodes));
  }

  /** path 변수가 있는 오퍼레이션이라 에러 스니펫에도 {@code pathParameters}를 선언한다(#151 규약 12). */
  private void performTipsError(
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
                ApiDocsTags.LIFE_TIPS,
                LIFE_TIPS_TIPS_SUMMARY,
                LIFE_TIPS_TIPS_DESCRIPTION,
                tipsPathParameters(),
                errorCodes));
  }
}
