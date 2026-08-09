package com.kohere.docs;

import static com.kohere.docs.ApiDocsFields.codeArrayField;
import static com.kohere.docs.ApiDocsFields.enumField;
import static com.kohere.docs.ApiDocsFields.errorNull;
import static com.kohere.docs.ApiDocsFields.field;
import static com.kohere.docs.ApiDocsFields.optCodeField;
import static com.kohere.docs.ApiDocsFields.optEnumField;
import static com.kohere.docs.ApiDocsFields.optField;
import static com.kohere.docs.ApiDocsParams.optCodeParam;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;

import com.kohere.diagnosis.domain.ArcStatus;
import com.kohere.diagnosis.domain.DiagnosisStatus;
import com.kohere.diagnosis.domain.District;
import com.kohere.diagnosis.domain.Purpose;
import com.kohere.diagnosis.domain.Region;
import com.kohere.diagnosis.domain.UniversityGroup;
import java.util.ArrayList;
import java.util.List;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.request.ParameterDescriptor;

/**
 * diagnosis 도메인 문서 테스트가 공유하는 코드 카탈로그·파라미터·필드 기술자(#151).
 *
 * <p><b>왜 별도 클래스인가</b> — v1({@code DiagnosisDocsTest})과 v2({@code DiagnosisV2DocsTest})가 같은 오퍼레이션을
 * 함께 캡처할 수 있다({@code POST /api/v1/diagnoses}·{@code GET /api/v1/diagnoses/{diagnosisId}}는 v2 파일의
 * 픽스처 경로이기도 하다). 같은 {@code (path, method, status)}의 필드 기술자는 합집합이 아니라 {@code (path, type)} 기준으로 접히고
 * 승자가 파일 순회 순서에 좌우되므로, 두 파일이 서로 다른 기술자를 쓰면 enum·optional이 조용히 사라진다({@link ApiDocsFields} 클래스 주석).
 * 그래서 <b>공유 오퍼레이션의 summary·description·에러코드 배열·응답 필드는 여기 한 벌만 둔다</b>.
 *
 * <p>추천 응답({@code content[]}·{@code markers[]}·{@code page})은 v1 §7과 v2-3이 같은 모양이라 경로가 달라 병합되지는 않지만
 * 같은 헬퍼를 공유해 두 문서가 어긋나지 않게 한다.
 */
public final class DiagnosisDocsFields {

  private DiagnosisDocsFields() {}

  // ---------------------------------------------------------------------------------------------
  // 코드 카탈로그 — enum 클래스가 없거나(MongoDB 카탈로그), 요청/응답 허용 집합이 다른 값들
  // ---------------------------------------------------------------------------------------------

  /**
   * 진단 문항의 제출 필드명({@code diagnosisQuestions.field}). enum 클래스가 아니라 MongoDB 카탈로그 문자열이라 직접 나열한다.
   *
   * <p>{@code regionRetry}는 v2 흐름의 ① 지역 0건 예외질문 전용이다 — v1 {@code GET /questions/{step}}은 정본 6슬롯만
   * 조회하므로 내려오지 않는다.
   */
  public static final List<String> QUESTION_FIELD_CODES =
      List.of(
          "region",
          "regionRetry",
          "purpose",
          "university",
          "district",
          "conditions",
          "monthlyRent",
          "arcStatus");

  /**
   * v1 단계 답 저장({@code POST /api/v1/diagnoses/answers})이 받는 제출 필드 7개 — 정본 6슬롯(③만 두 필드)이다.
   *
   * <p>{@link #QUESTION_FIELD_CODES}와 <b>허용 집합이 다르다</b> — v2 전용 {@code regionRetry}를 v1 요청에 실으면
   * {@code DiagnosisFlowStep.ofField}가 거절해 400이므로, 요청 스키마에는 넣지 않는다(규약 7-b).
   */
  public static final List<String> ANSWER_FIELD_CODES =
      List.of(
          "region", "purpose", "university", "district", "conditions", "monthlyRent", "arcStatus");

  /**
   * 문항의 선택 방식({@code select.type}). MongoDB 카탈로그 문자열이며 enum 클래스가 없다.
   *
   * <p>{@code NUMBER}는 존재하지 않는 값이다 — 숫자 2개를 입력받는 ⑤ 월세 문항은 {@code NUMBER_RANGE}다.
   */
  public static final List<String> SELECT_TYPE_CODES = List.of("SINGLE", "MULTI", "NUMBER_RANGE");

  /**
   * ④ 주거 조건 요청({@code codes[]})의 허용 코드 8개. 파생 조건 {@code NO_ARC}는 ⑥ {@code arcStatus} 답에서 서버가 만들어 넣는
   * 값이라 사용자가 직접 고를 수 없다({@code DiagnosisCondition.userSelectable()}).
   */
  public static final List<String> SELECTABLE_CONDITION_CODES =
      List.of(
          "MOVE_IN_NOW",
          "FEMALE_ONLY",
          "MEALS_INCLUDED",
          "DOUBLE_ROOM",
          "PRIVATE_BATH",
          "ENGLISH_OK",
          "ADDRESS_REGISTRATION",
          "NO_MAINT_FEE");

  /** 진단 응답의 {@code conditions[]} 허용 코드 9개 — 요청 8개 + 서버 파생 {@code NO_ARC}. */
  public static final List<String> DIAGNOSIS_CONDITION_CODES = withNoArc();

  /** 추천 매물 카드의 주거 유형 코드(listing {@code ListingType}). */
  public static final List<String> LISTING_TYPE_CODES =
      List.of("GOSHIWON", "CO_LIVING", "SHARE_HOUSE", "OTHER");

  /** 추천 매물 카드의 조건 배지 코드(listing {@code ConditionTag}) — 진단 조건과 이름이 1:1로 통일돼 있다. */
  public static final List<String> LISTING_CONDITION_CODES = withNoArc();

  /** 진단 이력 정렬 허용값({@code DiagnosisService.HISTORY_SORT_KEYS} × 방향). */
  public static final List<String> HISTORY_SORT_VALUES =
      List.of("submittedAt,desc", "submittedAt,asc");

  /** 추천 정렬 허용값({@code DiagnosisRecommendationReader.SORT_KEYS} × 방향). */
  public static final List<String> RECOMMENDATION_SORT_VALUES =
      List.of(
          "recommended,desc",
          "recommended,asc",
          "price,asc",
          "price,desc",
          "distance,asc",
          "distance,desc");

  private static List<String> withNoArc() {
    List<String> codes = new ArrayList<>(SELECTABLE_CONDITION_CODES);
    codes.add("NO_ARC");
    return List.copyOf(codes);
  }

  // ---------------------------------------------------------------------------------------------
  // 공유 오퍼레이션 ① POST /api/v1/diagnoses — 진단 확정
  // ---------------------------------------------------------------------------------------------

  public static final String SUBMIT_SUMMARY = "진행 중 진단 확정";

  public static final String SUBMIT_DESCRIPTION =
      """
      단계별로 저장해 둔 진행 중 진단을 확정해 이력에 남긴다.

      **인증**

      - 회원 전용이다. `Authorization: Bearer <accessToken>`가 필수이며 비회원은 v2 흐름(`POST /api/v2/diagnoses/start`)을 쓴다.
      - 확정 대상은 요청 본문이 아니라 토큰의 사용자로 찾는다. 사용자당 진행 중 진단은 1건이다.

      **동작 규칙**

      - 요청 본문이 없다. `POST /api/v1/diagnoses/answers`로 ①~⑥을 모두 저장한 뒤 호출한다.
      - 확정되면 상태가 `IN_PROGRESS`에서 `COMPLETED`로 바뀌고 `diagnosisId`·`submittedAt`이 발급된다.
      - 응답은 `201 Created`이며 `Location: /api/v1/diagnoses/{diagnosisId}` 헤더가 함께 실린다.
      - ⑥ `arcStatus`가 `NO_ARC`이면 서버가 파생 조건 `NO_ARC`를 `conditions`에 더한다(④ 최대 3개 제한과 무관).
      - 재진단은 확정 뒤 답을 다시 저장하면 서버가 새 진행 중 진단을 만든다.

      **에러 코드**

      - `400 INVALID_INPUT` — 진행 중 진단 없음, 단계 미완료, 저장된 답 재검증 실패
      - `401 UNAUTHENTICATED` — 토큰 없음 또는 위조
      - `401 TOKEN_EXPIRED` — 액세스 토큰 만료
      """;

  // 요청 본문이 없는 오퍼레이션이라 MALFORMED_REQUEST에 도달할 수 없다 — 400은 INVALID_INPUT 하나다.
  public static final String[] SUBMIT_400 = {"INVALID_INPUT"};
  public static final String[] SUBMIT_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};

  /** {@code POST /api/v1/diagnoses} 201 응답. */
  public static List<FieldDescriptor> submitResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.diagnosisId", JsonFieldType.NUMBER, "확정된 진단 식별자. Location 헤더의 마지막 세그먼트와 같다"),
        enumField("data.status", DiagnosisStatus.class, "진단 상태 — 확정 직후라 항상 `COMPLETED`"),
        field("data.submittedAt", JsonFieldType.STRING, "확정(제출) 시각(ISO-8601 UTC)"),
        errorNull());
  }

  // ---------------------------------------------------------------------------------------------
  // 공유 오퍼레이션 ② GET /api/v1/diagnoses/{diagnosisId} — 진단 단건 상세
  // ---------------------------------------------------------------------------------------------

  public static final String DETAIL_SUMMARY = "진단 단건 상세";

  public static final String DETAIL_DESCRIPTION =
      """
      확정된 진단 1건의 입력값 전체를 다시 보여준다(입력 다시 보기 화면).

      **인증**

      - 회원 전용이며 본인 소유 진단만 조회된다. 타인 소유는 `403 FORBIDDEN`이다.
      - 게스트가 v2로 만든 진단은 신원 종류가 달라 회원 토큰으로 조회해도 `403 FORBIDDEN`이다.

      **응답 규칙**

      - ② `purpose`에 따라 ③이 갈린다 — `STUDY`면 `university`가 채워지고 `district`는 `null`, `NON_STUDY`면 그 반대다.
      - `conditions[]`에는 ④에서 고른 조건(최대 3개)과 ⑥ `arcStatus=NO_ARC`에서 서버가 파생한 `NO_ARC`가 함께 들어간다.
      - 금액은 KRW 정수이고 시각은 ISO-8601 UTC다.
      - v2가 남기는 폐기 기록(`DISCARDED`)은 없는 것처럼 `404 DIAGNOSIS_NOT_FOUND`로 거절한다.

      **에러 코드**

      - `401 UNAUTHENTICATED` — 토큰 없음 또는 위조
      - `401 TOKEN_EXPIRED` — 액세스 토큰 만료
      - `403 FORBIDDEN` — 타인 소유 진단 접근
      - `404 DIAGNOSIS_NOT_FOUND` — 진단이 존재하지 않거나 폐기 기록
      """;

  public static final String[] DETAIL_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  public static final String[] DETAIL_403 = {"FORBIDDEN"};
  public static final String[] DETAIL_404 = {"DIAGNOSIS_NOT_FOUND"};

  /** {@code GET /api/v1/diagnoses/{diagnosisId}} 200 응답. */
  public static List<FieldDescriptor> detailResponseFields() {
    List<FieldDescriptor> fields = new ArrayList<>();
    fields.add(field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"));
    fields.addAll(diagnosisSummaryFields("data."));
    fields.add(enumField("data.status", DiagnosisStatus.class, "진단 상태 — 확정 진단이므로 `COMPLETED`"));
    fields.add(field("data.submittedAt", JsonFieldType.STRING, "확정 시각(ISO-8601 UTC)"));
    fields.add(errorNull());
    return List.copyOf(fields);
  }

  /** 진단 식별자 path 파라미터(성공·에러 스니펫 공용). */
  public static ParameterDescriptor[] diagnosisIdPathParameters() {
    return new ParameterDescriptor[] {
      parameterWithName("diagnosisId").description("진단 식별자(본인 소유 확정 진단)")
    };
  }

  // ---------------------------------------------------------------------------------------------
  // 진단 입력 요약(이력 항목·단건 상세 공용 형태)
  // ---------------------------------------------------------------------------------------------

  /**
   * 진단 입력 요약 필드. {@code prefix}는 {@code "data."} 또는 {@code "data.content[]."}다.
   *
   * <p><b>{@code GET /latest}에는 쓰지 않는다</b> — 그쪽은 {@code completed=false}일 때 같은 필드가 전부 {@code null}로
   * 실려 optional이어야 하는데, 여기서 optional을 낮추면 이력·상세의 「항상 있다」 계약까지 함께 풀린다.
   */
  public static List<FieldDescriptor> diagnosisSummaryFields(String prefix) {
    return List.of(
        field(prefix + "diagnosisId", JsonFieldType.NUMBER, "진단 식별자"),
        enumField(prefix + "region", Region.class, "① 지역(단일 선택)"),
        enumField(prefix + "purpose", Purpose.class, "② 입국 목적(단일 선택) — ③ 문항을 가른다"),
        optEnumField(
            prefix + "university",
            UniversityGroup.class,
            "③ 대학 그룹 — `purpose=STUDY`일 때만 채워지고 `NON_STUDY`면 `null`이다"),
        optEnumField(
            prefix + "district",
            District.class,
            "③ 지역(구) — `purpose=NON_STUDY`일 때만 채워지고 `STUDY`면 `null`이다"),
        codeArrayField(
            prefix + "conditions",
            DIAGNOSIS_CONDITION_CODES,
            "주거 조건 코드 목록 — ④에서 고른 최대 3개 + ⑥ `arcStatus=NO_ARC`일 때 서버가 더하는 `NO_ARC`"),
        field(prefix + "monthlyRentMin", JsonFieldType.NUMBER, "⑤ 월세 범위 하한(KRW 정수)"),
        field(prefix + "monthlyRentMax", JsonFieldType.NUMBER, "⑤ 월세 범위 상한(KRW 정수)"),
        enumField(prefix + "arcStatus", ArcStatus.class, "⑥ ARC(외국인등록증) 발급 상태(단일 선택)"));
  }

  // ---------------------------------------------------------------------------------------------
  // 추천 응답(v1 §7 · v2-3 공용 형태)
  // ---------------------------------------------------------------------------------------------

  /**
   * 추천 매물 카드와 지도 마커. 0건 응답도 같은 헬퍼를 쓰도록 <b>배열 원소 필드는 전부 optional</b>이다 — 0건이면 배열이 비어 원소 자체가 없다(값이
   * null인 것이 아니다).
   */
  public static List<FieldDescriptor> recommendationContentFields() {
    return List.of(
        field(
            "data.content",
            JsonFieldType.ARRAY,
            "추천 매물 카드 목록(현재 페이지). 조건에 맞는 매물이 0건이면 빈 배열이며 에러가 아니다"),
        optField(
            "data.content[].listingId",
            JsonFieldType.STRING,
            "매물 식별자(ObjectId hex 문자열) — `markers[]`와 이 값으로 연결한다"),
        optField("data.content[].title", JsonFieldType.STRING, "매물 제목(사용자 언어로 선택된 문자열)"),
        optCodeField(
            "data.content[].type.code", LISTING_TYPE_CODES, "주거 유형의 언어 무관 서버 코드. 필터 재요청·내부 비교에 쓴다"),
        optField("data.content[].type.label", JsonFieldType.STRING, "주거 유형 표시명(사용자 언어). 화면에 쓴다"),
        optField("data.content[].monthlyRentMin", JsonFieldType.NUMBER, "매물 월세 범위 하한(KRW 정수)"),
        optField("data.content[].monthlyRentMax", JsonFieldType.NUMBER, "매물 월세 범위 상한(KRW 정수)"),
        optField("data.content[].minDeposit", JsonFieldType.NUMBER, "매물 보증금 범위 하한(KRW 정수)"),
        optField("data.content[].maxDeposit", JsonFieldType.NUMBER, "매물 보증금 범위 상한(KRW 정수)"),
        optField(
            "data.content[].thumbnailUrl", JsonFieldType.STRING, "썸네일 URL. 등록된 이미지가 없으면 `null`"),
        optField("data.content[].lat", JsonFieldType.NUMBER, "매물 위도(WGS84)"),
        optField("data.content[].lng", JsonFieldType.NUMBER, "매물 경도(WGS84)"),
        optField(
            "data.content[].conditions",
            JsonFieldType.ARRAY,
            "카드 조건 배지 목록. `label`을 표시하고 `code`는 필터 재요청에 쓴다"),
        optCodeField(
            "data.content[].conditions[].code", LISTING_CONDITION_CODES, "조건의 언어 무관 서버 코드"),
        optField("data.content[].conditions[].label", JsonFieldType.STRING, "조건 배지 표시 문구(사용자 언어)"),
        field("data.markers", JsonFieldType.ARRAY, "현재 페이지 매물의 지도 마커. 0건이면 빈 배열"),
        optField(
            "data.markers[].listingId",
            JsonFieldType.STRING,
            "마커가 가리키는 매물 식별자 — `content[]`와 같은 값이다"),
        optField("data.markers[].lat", JsonFieldType.NUMBER, "마커 위도(WGS84)"),
        optField("data.markers[].lng", JsonFieldType.NUMBER, "마커 경도(WGS84)"));
  }

  /** 오프셋 페이지 메타(api-design-guide §4-1). */
  public static List<FieldDescriptor> pageFields() {
    return List.of(
        field("data.page.number", JsonFieldType.NUMBER, "현재 페이지 번호(0-base)"),
        field("data.page.size", JsonFieldType.NUMBER, "페이지 크기"),
        field("data.page.totalElements", JsonFieldType.NUMBER, "전체 건수(0건이면 0)"),
        field("data.page.totalPages", JsonFieldType.NUMBER, "전체 페이지 수(0건이면 0)"),
        field("data.page.hasNext", JsonFieldType.BOOLEAN, "다음 페이지 존재 여부"));
  }

  /** 추천 조회 query 파라미터(v1 §7 · v2-3 공용). */
  public static ParameterDescriptor[] recommendationQueryParameters() {
    return new ParameterDescriptor[] {
      parameterWithName("page").optional().description("0-base 페이지 번호(기본 `0`, 0 이상)"),
      parameterWithName("size").optional().description("페이지 크기(기본 `20`, 1~100)"),
      optCodeParam(
          "sort",
          RECOMMENDATION_SORT_VALUES,
          "정렬 — `키,방향` 한 문자열. 허용 키는 `recommended`·`price`·`distance`, 방향은 `asc`·`desc`(기본 `recommended,desc`)")
    };
  }

  /** 진단 이력 query 파라미터. */
  public static ParameterDescriptor[] historyQueryParameters() {
    return new ParameterDescriptor[] {
      parameterWithName("page").optional().description("0-base 페이지 번호(기본 `0`, 0 이상)"),
      parameterWithName("size").optional().description("페이지 크기(기본 `20`, 1~100)"),
      optCodeParam(
          "sort",
          HISTORY_SORT_VALUES,
          "정렬 — `키,방향` 한 문자열. 허용 키는 `submittedAt` 하나이고 방향은 `asc`·`desc`(기본 `submittedAt,desc`)")
    };
  }

  /** 진단 문항 표(v1 단계별 조회·v2 흐름 description 공용). 마크다운 표가 그대로 렌더된다. */
  public static final String QUESTION_TABLE =
      """
      | 단계 | `field` | `select.type` | 선택지(`options[].code`) |
      | --- | --- | --- | --- |
      | ① 지역 | `region` | `SINGLE` | `SEOUL` · `BUSAN` · `GYEONGGI` |
      | ② 입국 목적 | `purpose` | `SINGLE` | `STUDY` · `NON_STUDY` |
      | ③ 대학(`STUDY`) | `university` | `SINGLE` | `HUFS_KHU_KOREA` · `SKKU_SUNGSHIN` · `SNU_CAU_SOONGSIL` · `HONGIK_YONSEI_EWHA` · `KONKUK_SEJONG_HYU` · `ETC` |
      | ③ 지역구(`NON_STUDY`) | `district` | `SINGLE` | `GURO_GU` · `YEONGDEUNGPO_GU` · `GEUMCHEON_GU` · `GWANAK_GU` · `DONGDAEMUN_GU` · `ETC` |
      | ④ 주거 조건 | `conditions` | `MULTI`(최대 3개) | `MOVE_IN_NOW` · `FEMALE_ONLY` · `MEALS_INCLUDED` · `DOUBLE_ROOM` · `PRIVATE_BATH` · `ENGLISH_OK` · `ADDRESS_REGISTRATION` · `NO_MAINT_FEE` |
      | ⑤ 월세 범위 | `monthlyRent` | `NUMBER_RANGE` | 없음(빈 배열) — `min`·`max` 두 숫자를 입력받는다 |
      | ⑥ ARC 발급 | `arcStatus` | `SINGLE` | `ARC_ISSUED` · `NO_ARC` |
      """;

  /** 예시 응답의 선택지가 테스트 시드라 실제 목록과 다르다는 주의(모든 문항 스니펫 공용). */
  public static final String SEED_NOTE =
      "예시 응답의 `options[]`는 문서 테스트가 심은 축약 시드라 실제 운영 목록보다 짧다."
          + " 실제 허용 코드는 위 표와 스키마 탭의 `enum` 목록이 정본이다.";

  /** 표시 언어 규칙(문항·라벨 번역 공용). */
  public static final String LANGUAGE_NOTE =
      "표시 문자열(`question`·`options[].label`)만 사용자 표시 언어로 번역되고 `code`는 언어와 무관하게 같다."
          + " 언어는 `users.lang`(미설정 시 `en`)을 따르며 미지원 언어는 `en`으로 폴백한다.";
}
