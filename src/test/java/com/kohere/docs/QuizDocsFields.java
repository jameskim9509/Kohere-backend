package com.kohere.docs;

import static com.kohere.docs.ApiDocsFields.enumField;
import static com.kohere.docs.ApiDocsFields.errorNull;
import static com.kohere.docs.ApiDocsFields.field;
import static com.kohere.docs.ApiDocsFields.optEnumField;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;

import com.kohere.gamification.domain.ChoiceKey;
import java.util.List;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.request.ParameterDescriptor;

/**
 * {@link ApiDocsTags#QUIZ} 태그 오퍼레이션의 문구 상수·필드 기술자(#151).
 *
 * <p>문서 문구는 오퍼레이션(path+method)당 상수 1벌({@code QUIZ_RANDOM_*}·{@code QUIZ_ANSWER_*})을 성공·에러 스니펫이 공유한다
 * — 생성기가 같은 오퍼레이션의 summary/description 중 첫 non-blank 하나만 채택하기 때문이다. 필드 기술자도 같은 이유로 한 벌만 둔다({@link
 * ApiDocsFields} 클래스 주석 참조).
 */
public final class QuizDocsFields {

  private QuizDocsFields() {}

  // ===== GET /api/v1/quizzes/random =====

  public static final String QUIZ_RANDOM_SUMMARY = "랜덤 퀴즈 조회";

  public static final String QUIZ_RANDOM_DESCRIPTION =
      """
      활성 퀴즈 풀에서 4지선다 1개를 무작위로 골라 표시 언어로 번역해 반환한다(정답 키·해설 미포함).

      **헤더**

      - `Authorization: Bearer <accessToken>` — 선택. 없으면 게스트로 응답한다.
      - 역할 게이트가 없다 — 게스트·세입자·임대인 모두 200이며 응답 스키마도 동일하다.
      - 서명이 깨진 토큰도 게스트로 처리해 200이지만, 만료된 access token은 게스트로 강등하지 않고 401 `TOKEN_EXPIRED`로 재발급을 유도한다.

      **응답 주의사항**

      - 표시 언어가 호출자에 따라 갈린다 — 게스트는 `en` 고정, 세입자는 본인이 고른 `lang`(미선택이면 `en` 폴백), 임대인은 `ko` 고정.
      - 채점은 이 응답이 아니라 `POST /api/v1/quizzes/{quizId}/answer`가 담당한다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 |
      | 404 | `QUIZ_NOT_FOUND` | 활성 퀴즈 풀이 비어 사용 가능한 퀴즈가 없음 |
      """;

  public static final String[] QUIZ_RANDOM_401 = {"TOKEN_EXPIRED"};
  public static final String[] QUIZ_RANDOM_404 = {"QUIZ_NOT_FOUND"};

  public static List<FieldDescriptor> randomResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.quizId", JsonFieldType.NUMBER, "퀴즈 식별자(채점 경로의 `quizId`로 사용)"),
        field(
            "data.question",
            JsonFieldType.STRING,
            "표시 언어로 번역된 문항 — 퀴즈 카탈로그에 그 언어 번역이 없으면 영어 폴백(현재 카탈로그는 `ko`·`en`뿐이라 `ja`는 항상 영어)"),
        enumField("data.choices[].key", ChoiceKey.class, "보기 키 — 언어와 무관하게 불변이며 채점은 이 키로 한다"),
        field("data.choices[].text", JsonFieldType.STRING, "표시 언어로 번역된 보기 텍스트(문항과 같은 영어 폴백)"),
        errorNull());
  }

  // ===== POST /api/v1/quizzes/{quizId}/answer =====

  public static final String QUIZ_ANSWER_SUMMARY = "퀴즈 정답 제출·채점";

  public static final String QUIZ_ANSWER_DESCRIPTION =
      """
      제출한 보기 키를 저장된 정답과 대조해 즉시 채점한다. 같은 퀴즈를 몇 번이든 다시 제출할 수 있고 결과도 매번 같다 — 제출 기록이 남지 않고 포인트도 지급되지 않는다.

      **헤더**

      - `Authorization: Bearer <accessToken>` — 선택. 없으면 게스트로 응답한다.
      - 역할 게이트가 없다 — 게스트·세입자·임대인 모두 200이며 응답 스키마도 동일하다.
      - 서명이 깨진 토큰도 게스트로 처리해 200이지만, 만료된 access token은 게스트로 강등하지 않고 401 `TOKEN_EXPIRED`로 재발급을 유도한다.

      **응답 주의사항**

      - `correctChoice`는 오답(`correct=false`)일 때만 내려간다. 정답이면 값이 null이 아니라 **필드 자체가 생략**된다.
      - 표시 언어가 호출자에 따라 갈린다 — 게스트는 `en` 고정, 세입자는 `lang`(미선택이면 `en`), 임대인은 `ko` 고정.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `selectedChoice`가 `A`~`D`가 아니거나 누락·빈 값 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 |
      | 404 | `QUIZ_NOT_FOUND` | 경로의 `quizId`에 해당하는 퀴즈가 없음 |
      """;

  public static final String[] QUIZ_ANSWER_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  public static final String[] QUIZ_ANSWER_401 = {"TOKEN_EXPIRED"};
  public static final String[] QUIZ_ANSWER_404 = {"QUIZ_NOT_FOUND"};

  /**
   * {@code quizId}는 서버 계약상 정수(Long)지만 스키마에는 문자열로 나간다 — {@code com.epages}의 {@code
   * parameterWithName(...).type(SimpleType.INTEGER)}는 {@link ParameterDescriptor}의 하위 타입이 아니라
   * {@code RequestDocumentation.pathParameters}에 넘길 수 없다. description으로 보완한다.
   */
  public static ParameterDescriptor[] answerPathParameters() {
    return new ParameterDescriptor[] {
      parameterWithName("quizId").description("채점 대상 퀴즈 ID(정수). 랜덤 조회 응답의 `data.quizId`를 그대로 쓴다")
    };
  }

  public static List<FieldDescriptor> answerRequestFields() {
    return List.of(
        enumField("selectedChoice", ChoiceKey.class, "선택한 보기 키. 그 외 값·빈 값·누락은 INVALID_INPUT"));
  }

  /**
   * 정답·오답 200 응답의 <b>공용</b> 기술자. 같은 {@code (path, method, status)}라 생성기가 {@code (path, type)} 기준
   * dedup·last-wins로 접기 때문에 두 벌로 나누면 승자가 파일 순회 순서에 좌우된다(#151 규약 2).
   *
   * <p>{@code data.correctChoice}는 정답 응답에서 {@code @JsonInclude(NON_NULL)}로 키가 사라지므로 {@code
   * optional}이다. 그 대가로 {@code quiz-answer-correct}가 {@code doesNotExist} 단정을 진다(규약 13).
   */
  public static List<FieldDescriptor> answerResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.quizId", JsonFieldType.NUMBER, "채점 대상 퀴즈 ID"),
        enumField("data.selectedChoice", ChoiceKey.class, "제출한 보기 키(요청 값 반향)"),
        field("data.correct", JsonFieldType.BOOLEAN, "정답 여부 — 서버가 저장된 정답과 대조해 판정한다"),
        optEnumField(
            "data.correctChoice",
            ChoiceKey.class,
            "정답 보기 키 — 오답일 때만 포함되고 정답이면 값이 null이 아니라 필드 자체가 생략된다"),
        field(
            "data.explanation",
            JsonFieldType.STRING,
            "해설(정답·오답 모두, 표시 언어로 번역 — 번역이 없는 언어는 영어 폴백이라 `ja`는 항상 영어)"),
        errorNull());
  }
}
