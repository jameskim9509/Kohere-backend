package com.kohere.docs;

import static com.kohere.docs.ApiDocsFields.codeField;
import static com.kohere.docs.ApiDocsFields.enumField;
import static com.kohere.docs.ApiDocsFields.errorNull;
import static com.kohere.docs.ApiDocsFields.field;
import static com.kohere.docs.ApiDocsFields.optCodeField;
import static com.kohere.docs.ApiDocsFields.optEnumField;
import static com.kohere.docs.ApiDocsFields.optField;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;

import com.kohere.user.domain.Gender;
import com.kohere.user.domain.Occupation;
import com.kohere.user.domain.UserStatus;
import com.kohere.user.domain.UserType;
import com.kohere.user.domain.VisaType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.request.ParameterDescriptor;

/**
 * {@code Users} 태그(프로필 조회·수정·탈퇴 + 내 차단 목록·해제)의 문구·에러코드 상수와 필드 기술자(#151). 회원 프로필 응답은 세입자·임대인
 * <b>합집합</b>이다.
 *
 * <p><b>왜 공용인가</b> — {@code GET /users/me}의 200 스니펫은 두 파일에서 캡처된다({@code AuthOnboardingDocsTest}의
 * {@code user-get-me}(세입자), {@code LandlordOnboardingDocsTest}의 {@code user-get-me-landlord}(임대인)).
 * 같은 {@code (path, method, status)}의 필드 기술자는 합집합이 아니라 {@code (path, type)} 기준 dedup·last-wins라, 두
 * 파일이 서로 다른 기술자를 쓰면 한쪽의 {@code optional}·enum이 조용히 사라지고 승자가 파일 순회 순서(로컬 NTFS ↔ CI ext4)에 좌우된다. 그래서
 * 양쪽이 <b>이 클래스의 같은 메서드</b>를 호출한다.
 *
 * <p><b>역할 전용 필드는 전부 optional</b> — 응답 DTO가 NON_NULL 직렬화라 값이 없으면 「null」이 아니라 <b>키 자체가 사라진다</b>. 문서가
 * 그만큼 느슨해지므로 호출부는 그 필드가 없어야 하는 케이스에 {@code jsonPath(...).doesNotExist()} 단정을 함께 둔다.
 *
 * <p>대응 스펙: docs/api/specs/01-auth-onboarding.md §5·§5-2(온보딩 응답의 {@code data.user})·§8(GET
 * /users/me)·§9(PATCH /users/me)·§10(DELETE /users/me)·§11·§12(차단 목록·해제).
 */
public final class UserDocsFields {

  private UserDocsFields() {}

  /**
   * 표시 언어 코드. {@code Language} enum이 있지만 와이어 값이 상수명({@code EN})이 아니라 ISO 639-1 <b>소문자</b>라 {@code
   * Enum.toString()}을 값으로 쓰는 {@code enumField}를 쓸 수 없다.
   */
  public static final List<String> LANG_CODES = List.of("en", "ko", "ja");

  /**
   * 국적 코드(ISO 3166-1 alpha-2). enum이 아니라 {@code countries} 테이블 카탈로그이며 <b>값 목록을 내려주는 API가 없다</b> —
   * 앱이 조회할 곳이 없으므로 여기서 전량 나열한다. 시드는 {@code
   * src/main/resources/db/migration/V4__countries_reference.sql}.
   */
  public static final List<String> COUNTRY_CODES =
      List.of(
          "KR", "CN", "VN", "US", "UZ", "TH", "PH", "NP", "MN", "JP", "ID", "KH", "RU", "IN", "MM");

  /** 토큰 타입. 서버가 상수로 채우며 {@code Bearer} 외의 값은 없다. */
  public static final List<String> TOKEN_TYPES = List.of("Bearer");

  /** 국적 코드 설명 — 요청·응답 양쪽에서 같은 문구를 쓴다. */
  private static final String COUNTRY_NOTE =
      "국적 ISO 3166-1 alpha-2 코드. 값 목록을 내려주는 API가 없어 여기 나열한 15개가 지원 코드의 전부다(countries 참조 시드)";

  // ---- GET /api/v1/users/me 오퍼레이션 문구(두 파일이 공유) ----

  public static final String ME_SUMMARY = "내 프로필 조회";

  public static final String ME_DESCRIPTION =
      """
      인증된 본인의 프로필 전체를 반환한다.

      인증: 정식(ACTIVE) 회원 본인. 온보딩 미완료(`PENDING`·`TERMS_AGREED`) 토큰으로 접근하면 403이다.

      응답 필드는 `userType`으로 갈린다. 스키마에 `(세입자만)`·`(임대인만)`이 붙은 필드는 다른 역할의 응답에서 **키 자체가 없다**(값이 null인 것이 아니다 — 응답 DTO가 `@JsonInclude(NON_NULL)`이다).
      - `TENANT`: `gender`·`visaType`을 받고 `phoneNumber`는 생략된다.
      - `LANDLORD`: `phoneNumber`를 받고 `gender`·`occupation`·`visaType`은 생략된다. 본인 조회라 `phoneNumber`는 평문이다.
      - `occupation`은 세입자도 선택값이라 미설정이면 생략된다.
      - `lang`은 세입자가 고르지 않았으면 생략된다(표시 시 `en` 폴백). 임대인은 서버가 `ko`로 고정 부여한다.
      - `country`·`countryName`·`countryFlag`는 임대인도 받는다 — 서버가 `KR`을 고정 부여한다.
      - `email`은 세입자·임대인 모두 소셜 로그인 provider 값으로 보유한다.

      에러: 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED`, 403 `AUTH_ONBOARDING_REQUIRED`, 404 `USER_NOT_FOUND`.
      """;

  public static final String[] ME_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  public static final String[] ME_403 = {"AUTH_ONBOARDING_REQUIRED"};
  public static final String[] ME_404 = {"USER_NOT_FOUND"};

  // ---- PATCH /api/v1/users/me 오퍼레이션 문구(두 파일이 공유) ----
  // GET과 마찬가지로 세입자 예시(AuthOnboardingDocsTest의 user-patch-me)와 임대인 예시
  // (LandlordOnboardingDocsTest의 user-patch-me-landlord)가 같은 오퍼레이션이라 문구·요청 필드 기술자를 공유한다.

  public static final String PATCH_ME_SUMMARY = "내 프로필 부분 수정";

  public static final String PATCH_ME_DESCRIPTION =
      """
      전송한 필드만 바꾸고 미전송 필드는 유지한다(미전송은 「값 비움」이 아니다). 응답은 수정된 프로필 전체이며 `GET /users/me`와 동일 스키마다.

      인증: 필수(정식 토큰 — `ACTIVE` 본인). 온보딩 미완료 토큰 접근은 403이다.

      수정 가능 필드가 `userType`으로 갈린다.
      - `TENANT`: `name`·`gender`·`birthDate`·`country`·`occupation`·`visaType`·`lang`·`marketingAgreed`.
      - `LANDLORD`: `name`·`phoneNumber`·`marketingAgreed`. `lang`은 `ko` 고정이라 바꿀 수 없고 `birthDate`는 조회 전용이다.
      - `nickname`(서버 배정)·`email`(소셜 provider 값)은 역할과 무관하게 이 경로의 수정 대상이 아니다.
      - 임대인 `phoneNumber` 변경은 새 번호를 SMS로 재인증한 뒤에만 반영된다 — 미인증·불일치는 422 `AUTH_PHONE_NOT_VERIFIED`다.
      - 400이 둘로 갈린다 — `gender`·`occupation`·`visaType` 허용 외 문자열과 `birthDate` 형식 위반은 역직렬화 단계에서 걸려 `MALFORMED_REQUEST`, `birthDate` 미래(`@Past`)·`country` 미존재·`lang` 미지원 코드는 `INVALID_INPUT`이다.
      - 응답의 역할 전용 필드(`(세입자만)`·`(임대인만)`)는 다른 역할의 응답에서 키 자체가 없다(값이 null인 것이 아니다).

      에러: 400 `INVALID_INPUT`·`MALFORMED_REQUEST`, 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED`, 403 `AUTH_ONBOARDING_REQUIRED`, 404 `USER_NOT_FOUND`, 422 `AUTH_PHONE_NOT_VERIFIED`.
      """;

  public static final String[] PATCH_ME_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  public static final String[] PATCH_ME_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  public static final String[] PATCH_ME_403 = {"AUTH_ONBOARDING_REQUIRED"};
  public static final String[] PATCH_ME_404 = {"USER_NOT_FOUND"};
  public static final String[] PATCH_ME_422 = {"AUTH_PHONE_NOT_VERIFIED"};

  // ---- DELETE /api/v1/users/me 오퍼레이션 문구 ----

  public static final String WITHDRAW_SUMMARY = "회원 탈퇴";

  public static final String WITHDRAW_DESCRIPTION =
      """
      본인 계정을 `WITHDRAWN`으로 전이하고 PII를 즉시 익명화하며 refresh 토큰을 일괄 무효화한다.

      인증: 필수. 온보딩 미완료(`PENDING`·`TERMS_AGREED`) 사용자도 탈퇴할 수 있다(온보딩 중단 정리 목적이라 403을 두지 않았다).

      - 성공 응답에는 본문이 없다(204).
      - Apple 연동 계정은 매핑 삭제 전에 Apple `/auth/revoke`로 연동까지 폐기한다(best-effort — Apple 장애여도 탈퇴는 완료).
      - 탈퇴 후 같은 토큰으로 프로필을 조회하면 404, 탈퇴를 재요청하면 409다.

      에러: 401 `UNAUTHENTICATED`·`TOKEN_EXPIRED`, 404 `USER_NOT_FOUND`, 409 `USER_ALREADY_WITHDRAWN`.
      """;

  public static final String[] WITHDRAW_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  public static final String[] WITHDRAW_404 = {"USER_NOT_FOUND"};
  public static final String[] WITHDRAW_409 = {"USER_ALREADY_WITHDRAWN"};

  // ---- GET /api/v1/users/me/blocks 오퍼레이션 문구 ----

  public static final String BLOCKS_LIST_SUMMARY = "내 차단 목록 조회";

  public static final String BLOCKS_LIST_DESCRIPTION =
      """
      내가 차단한 상대 목록을 페이지로 조회한다.

      인증: 정식(ACTIVE) 회원 본인.

      차단은 예약 문맥(`POST /api/v1/bookings/{bookingId}/block`)에서만 생성된다.
      """;

  // ---- DELETE /api/v1/users/me/blocks/{userId} 오퍼레이션 문구 ----

  public static final String UNBLOCK_SUMMARY = "차단 해제";

  public static final String UNBLOCK_DESCRIPTION =
      """
      차단을 해제한다. 차단하지 않은 상대를 해제해도 204다(멱등).

      인증: 정식(ACTIVE) 회원 본인. 본문·응답 본문이 없다.
      """;

  // ---- 필드 기술자 ----

  /**
   * 회원 프로필 공통 필드(세입자·임대인 합집합). {@code prefix}는 {@code "data."}(GET·PATCH /users/me 응답) 또는 {@code
   * "data.user."}(온보딩 응답의 {@code UserProfileView})다 — 두 DTO는 이 필드 집합을 공유하고, {@code
   * termsOfServiceAgreed}·{@code privacyPolicyAgreed}만 프로필 응답에 더 있다.
   */
  public static List<FieldDescriptor> profileFields(String prefix) {
    return List.of(
        field(prefix + "id", JsonFieldType.NUMBER, "회원 ID"),
        enumField(prefix + "userType", UserType.class, "회원 역할 — 아래 역할 전용 필드의 유무가 이 값으로 갈린다"),
        optField(
            prefix + "name",
            JsonFieldType.STRING,
            "이름(성·이름을 합친 단일 이름). 소셜 로그인 시 provider 값으로 채우며 provider가 주지 않으면 생략된다(#192)"),
        field(prefix + "nickname", JsonFieldType.STRING, "닉네임(서버가 형용사+사물로 배정, 전역 유니크·수정 불가)"),
        optEnumField(prefix + "gender", Gender.class, "성별(세입자만)"),
        field(prefix + "birthDate", JsonFieldType.STRING, "생년월일(YYYY-MM-DD)"),
        codeField(prefix + "country", COUNTRY_CODES, COUNTRY_NOTE + ". 임대인은 서버가 KR로 고정 부여한다"),
        optField(
            prefix + "countryName",
            JsonFieldType.STRING,
            "국가 표시명(서버가 countries 참조로 resolve, 예 South Korea)"),
        optField(prefix + "countryFlag", JsonFieldType.STRING, "국기 이미지 URL(flagcdn.com SVG)"),
        optCodeField(
            prefix + "lang",
            LANG_CODES,
            "표시 언어 ISO 639-1 소문자(UPPER_SNAKE 예외) — 세입자 선택값(미선택 시 표시는 en 폴백), 임대인은 ko 고정"),
        optEnumField(prefix + "occupation", Occupation.class, "직업(세입자만, 온보딩 선택값 #187)"),
        field(
            prefix + "email",
            JsonFieldType.STRING,
            "이메일(소셜 로그인 provider 진본 — 세입자·임대인 공통 보유, 본인 조회라 평문, #192)"),
        optEnumField(prefix + "visaType", VisaType.class, "비자정보(세입자만) — API는 상수명, DB 저장은 표시 라벨"),
        optField(
            prefix + "phoneNumber",
            JsonFieldType.STRING,
            "연락처(임대인만) — GET/PATCH /users/me는 본인 조회라 평문, 온보딩 응답은 마스킹(010-****-5678)"),
        enumField(
            prefix + "status", UserStatus.class, "회원 상태 — 이 응답에서는 항상 ACTIVE(미완료는 403, 탈퇴는 404)"),
        field(prefix + "marketingAgreed", JsonFieldType.BOOLEAN, "마케팅 수신 동의 여부"),
        field(prefix + "createdAt", JsonFieldType.STRING, "가입 시각(ISO-8601 UTC)"));
  }

  /**
   * {@code GET /users/me}·{@code PATCH /users/me} 200 응답. 두 오퍼레이션의 응답 스키마는 동일하다(스펙 §9 「수정된 프로필 전체를
   * GET /users/me와 동일 스키마로 반환」).
   */
  public static List<FieldDescriptor> meResponseFields() {
    List<FieldDescriptor> descriptors = new ArrayList<>();
    descriptors.add(field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"));
    descriptors.addAll(profileFields("data."));
    descriptors.add(field("data.termsOfServiceAgreed", JsonFieldType.BOOLEAN, "이용약관 동의 여부"));
    descriptors.add(field("data.privacyPolicyAgreed", JsonFieldType.BOOLEAN, "개인정보처리방침 동의 여부"));
    descriptors.add(errorNull());
    return List.copyOf(descriptors);
  }

  /**
   * {@code PATCH /users/me} 요청 본문(세입자·임대인 <b>합집합</b>). 전 필드가 선택이라 역할별로 보내는 부분집합이 다르다 — 응답과 마찬가지로 같은
   * 오퍼레이션의 스니펫이 서로 다른 기술자를 쓰면 {@code (path, type)} dedup에서 한쪽이 조용히 사라지므로 두 파일이 이 메서드를 함께 호출한다.
   */
  public static List<FieldDescriptor> patchRequestFields() {
    return List.of(
        optField("name", JsonFieldType.STRING, "이름(세입자·임대인 공통 단일 이름, 선택 — 빈 문자열 불가, #192)"),
        optEnumField("gender", Gender.class, "성별(세입자만·선택)"),
        optField("birthDate", JsonFieldType.STRING, "생년월일 YYYY-MM-DD(세입자 전용·선택, 과거 날짜만)"),
        optCodeField(
            "country",
            COUNTRY_CODES,
            "국적 ISO 3166-1 alpha-2 코드(세입자만·선택). 값 목록을 내려주는 API가 없어 여기 나열한 15개가 전부다(countries 참조 시드)"),
        optEnumField("occupation", Occupation.class, "직업(세입자만·선택)"),
        optEnumField("visaType", VisaType.class, "비자정보(세입자만·선택)"),
        optCodeField(
            "lang", LANG_CODES, "표시 언어 ISO 639-1 소문자(세입자 전용·선택 — 목록 밖 값·빈 문자열은 INVALID_INPUT)"),
        optField(
            "phoneNumber",
            JsonFieldType.STRING,
            "연락처(임대인만·선택) — 새 번호는 SMS 재인증(§4-1·§4-2) 후에만 반영되고 미인증이면 422"),
        optField("marketingAgreed", JsonFieldType.BOOLEAN, "마케팅 수신 동의(선택)"));
  }

  /**
   * 온보딩 응답의 {@code data.user}({@code UserProfileView})와 정식 토큰 3종. 세입자 {@code POST
   * /auth/onboarding}과 임대인 {@code POST /auth/landlord/onboarding}이 같은 응답 타입({@code
   * OnboardingResponse})을 쓴다.
   */
  public static List<FieldDescriptor> onboardingResponseFields() {
    List<FieldDescriptor> descriptors = new ArrayList<>();
    descriptors.add(field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"));
    descriptors.addAll(profileFields("data.user."));
    descriptors.add(codeField("data.tokenType", TOKEN_TYPES, "토큰 타입 — 항상 Bearer"));
    descriptors.add(
        field(
            "data.accessToken",
            JsonFieldType.STRING,
            "정식 access 토큰(JWT, onboardingCompleted=true — ROLE_USER)"));
    descriptors.add(field("data.refreshToken", JsonFieldType.STRING, "정식 refresh 토큰(불투명)"));
    descriptors.add(field("data.expiresIn", JsonFieldType.NUMBER, "access 토큰 만료까지 초(3600)"));
    descriptors.add(errorNull());
    return List.copyOf(descriptors);
  }

  /** {@code GET /users/me/blocks} 페이지 파라미터. */
  public static ParameterDescriptor[] blocksListQueryParameters() {
    return new ParameterDescriptor[] {
      parameterWithName("page").optional().description("0-base 페이지 번호(기본 0)"),
      parameterWithName("size").optional().description("페이지 크기(기본 20, 최대 100)")
    };
  }

  /** {@code GET /users/me/blocks} 200 응답. */
  public static List<FieldDescriptor> blocksListResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부"),
        field("data.content[].userId", JsonFieldType.NUMBER, "차단한 상대 식별자"),
        field("data.content[].name", JsonFieldType.STRING, "차단한 상대 표시명(마스킹 수준 확인 필요)"),
        field("data.content[].blockedAt", JsonFieldType.STRING, "차단 시각(UTC)"),
        field("data.page.number", JsonFieldType.NUMBER, "현재 페이지 번호"),
        field("data.page.size", JsonFieldType.NUMBER, "페이지 크기"),
        field("data.page.totalElements", JsonFieldType.NUMBER, "전체 건수"),
        field("data.page.totalPages", JsonFieldType.NUMBER, "전체 페이지 수"),
        field("data.page.hasNext", JsonFieldType.BOOLEAN, "다음 페이지 존재 여부"),
        errorNull());
  }

  /** {@code DELETE /users/me/blocks/{userId}} 경로 파라미터. */
  public static ParameterDescriptor[] unblockPathParameters() {
    return new ParameterDescriptor[] {parameterWithName("userId").description("차단을 해제할 상대 식별자")};
  }
}
