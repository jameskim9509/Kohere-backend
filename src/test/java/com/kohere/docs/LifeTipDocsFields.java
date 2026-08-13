package com.kohere.docs;

import static com.kohere.docs.ApiDocsFields.errorNull;
import static com.kohere.docs.ApiDocsFields.field;
import static com.kohere.docs.ApiDocsFields.optField;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;

import java.util.List;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.request.ParameterDescriptor;

/**
 * {@link ApiDocsTags#LIFE_TIPS} 태그 오퍼레이션의 문구 상수·필드 기술자(#151).
 *
 * <p>문서 문구는 오퍼레이션(path+method)당 상수 1벌({@code LIFE_TIPS_TOPICS_*}·{@code LIFE_TIPS_TIPS_*})을 성공·에러
 * 스니펫이 공유한다 — 생성기가 같은 오퍼레이션의 summary/description 중 첫 non-blank 하나만 채택하므로, 에러 스니펫이 제 문구를 따로 들고 있으면
 * 그게 성공 오퍼레이션의 제목·설명을 차지한다. 필드 기술자도 같은 이유로 한 벌만 둔다({@link ApiDocsFields} 클래스 주석 참조).
 */
public final class LifeTipDocsFields {

  private LifeTipDocsFields() {}

  // ===== GET /api/v1/life-tips/topics =====

  public static final String LIFE_TIPS_TOPICS_SUMMARY = "생활 팁 주제 목록 조회";

  public static final String LIFE_TIPS_TOPICS_DESCRIPTION =
      """
      생활 팁 주제 전체를 노출 순서대로 반환한다. 페이지 객체 없이 배열 하나만 담긴다.

      **헤더**

      - `Authorization: Bearer <accessToken>` — 선택. 없으면 게스트로 응답한다.
      - 서명이 깨진 토큰도 게스트로 처리해 200을 반환한다.
      - 역할 게이트가 없다 — 게스트·세입자·임대인 모두 200이다.

      **응답 주의사항**

      - 표시 언어가 호출자에 따라 갈린다 — 게스트는 `en` 고정, 세입자는 본인이 고른 `lang`(미선택이면 `en`으로 폴백 — 온보딩 미완료 회원도 여기 해당), 임대인은 `ko` 고정.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 — 게스트로 강등하지 않으니 재발급 후 재시도 |
      """;

  public static final String[] LIFE_TIPS_TOPICS_401 = {"TOKEN_EXPIRED"};

  public static List<FieldDescriptor> topicsResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field(
            "data.topics[].code",
            JsonFieldType.STRING,
            "언어 무관 주제 식별 코드. 주제별 팁 조회(`GET /api/v1/life-tips/topics/{topicCode}/tips`)의 경로 값으로 그대로 쓴다"),
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

  // ===== GET /api/v1/life-tips/topics/{topicCode}/tips =====

  public static final String LIFE_TIPS_TIPS_SUMMARY = "주제별 생활 팁 목록 조회";

  public static final String LIFE_TIPS_TIPS_DESCRIPTION =
      """
      고른 주제에 속한 생활 팁(제목·내용·사진) 전체를 노출 순서대로 반환한다. 페이지 객체 없이 배열 하나만 담긴다.

      **헤더**

      - `Authorization: Bearer <accessToken>` — 선택. 없으면 게스트로 응답한다.
      - 서명이 깨진 토큰도 게스트로 처리해 200을 반환한다.
      - 역할 게이트가 없다 — 게스트·세입자·임대인 모두 200이다.

      **응답 주의사항**

      - 표시 언어가 호출자에 따라 갈린다 — 게스트는 `en` 고정, 세입자는 본인이 고른 `lang`(미선택이면 `en`으로 폴백 — 온보딩 미완료 회원도 여기 해당), 임대인은 `ko` 고정.
      - 사진이 없는 팁은 `imageUrl`이 `null`이다(필드는 남는다).
      - 주제는 있으나 팁이 0건이면 `tips: []`다(에러 아님).

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 — 게스트로 강등하지 않으니 재발급 후 재시도 |
      | 404 | `LIFE_TIP_TOPIC_NOT_FOUND` | 경로의 `topicCode`가 카탈로그에 없음 |
      """;

  public static final String[] LIFE_TIPS_TIPS_401 = {"TOKEN_EXPIRED"};
  public static final String[] LIFE_TIPS_TIPS_404 = {"LIFE_TIP_TOPIC_NOT_FOUND"};

  /**
   * 주제 코드는 <b>허용값을 싣지 않는다</b>.
   *
   * <p>{@code codeParam}으로 값을 실으면 스펙에 {@code enum}이 박혀 Swagger UI가 이 경로 변수를 드롭다운으로 잠근다. 주제는 enum이
   * 아니라 DB 카탈로그({@code lifeTipTopics})라 배포 없이 늘어나는데, 그러면 새로 넣은 주제를 UI에서 고를 수 없다 — 서버는
   * {@code @PathVariable String}으로 그대로 받아 정상 처리하는데도 그렇다.
   */
  public static ParameterDescriptor[] tipsPathParameters() {
    return new ParameterDescriptor[] {
      parameterWithName("topicCode")
          .description("주제 코드(UPPER_SNAKE). `GET /api/v1/life-tips/topics` 응답의 `topics[].code`를 쓴다")
    };
  }

  public static List<FieldDescriptor> tipsResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.tips[].id", JsonFieldType.STRING, "팁 식별자(ObjectId hex, 언어 무관)"),
        field("data.tips[].title", JsonFieldType.STRING, "표시 언어로 번역된 제목"),
        field("data.tips[].content", JsonFieldType.STRING, "표시 언어로 번역된 내용"),
        optField("data.tips[].imageUrl", JsonFieldType.STRING, "사진 URL(언어 무관). 사진이 없는 팁은 `null`"),
        errorNull());
  }
}
