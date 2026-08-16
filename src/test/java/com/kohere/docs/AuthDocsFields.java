package com.kohere.docs;

import static com.kohere.docs.ApiDocsFields.codeField;
import static com.kohere.docs.ApiDocsFields.enumField;
import static com.kohere.docs.ApiDocsFields.errorNull;
import static com.kohere.docs.ApiDocsFields.field;
import static com.kohere.docs.ApiDocsFields.optCodeField;
import static com.kohere.docs.ApiDocsFields.optEnumField;
import static com.kohere.docs.ApiDocsFields.optField;
import static com.kohere.docs.UserDocsFields.COUNTRY_CODES;
import static com.kohere.docs.UserDocsFields.LANG_CODES;
import static com.kohere.docs.UserDocsFields.TOKEN_TYPES;

import com.kohere.auth.domain.Provider;
import com.kohere.user.domain.Gender;
import com.kohere.user.domain.Occupation;
import com.kohere.user.domain.UserStatus;
import com.kohere.user.domain.VisaType;
import java.util.List;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;

/**
 * {@code Auth} 태그({@code /api/v1/auth/**} 11개 오퍼레이션)의 문구·에러코드 상수와 요청/응답 필드 기술자(#151).
 *
 * <p><b>왜 한 클래스인가</b> — 같은 태그의 오퍼레이션이 {@code AuthOnboardingDocsTest}(세입자 트랙: 소셜 로그인·약관 동의·이메일
 * 인증·온보딩·재발급·로그아웃)와 {@code LandlordOnboardingDocsTest}(임대인 트랙: 연락처 SMS 인증·사업자등록번호 검증·임대인 온보딩) 두 파일에
 * 걸쳐 문서화된다. 오퍼레이션(path+method)당 summary/description은 <b>1벌</b>만 두고 그 오퍼레이션의 성공·에러 스니펫이 전부 같은 문자열·같은
 * 태그를 써야 하며(생성기가 같은 {@code (path, method)} 모델의 문구 중 첫 non-blank 하나만 채택한다), 같은 {@code (path, method,
 * status)}의 필드 기술자는 {@code (path, type)} 기준 dedup·last-wins라 승자가 파일 순회 순서에 좌우된다({@link
 * ApiDocsFields} 클래스 주석 참조). 그래서 문구·에러코드 배열·필드 기술자를 태그 단위로 여기 한 벌만 둔다.
 *
 * <p>{@code error} 코드 배열은 <b>오퍼레이션+status 단위</b> 상수다. 대응 스펙: docs/api/specs/01-auth-onboarding.md.
 */
public final class AuthDocsFields {

  private AuthDocsFields() {}

  // ── 소셜 로그인 — POST /api/v1/auth/social-login ────────────────────────────

  public static final String SOCIAL_LOGIN_SUMMARY = "소셜 로그인";

  public static final String SOCIAL_LOGIN_DESCRIPTION =
      """
      소셜 자격(Apple/Google)을 검증하고 서버 토큰을 발급한다. 신규면 계정을 만들고 온보딩 전용 토큰만 준다.

      **헤더**

      - 인증 불필요 — 토큰 없이 호출한다.

      **요청 주의사항**

      - provider별 자격이 다르다 — `GOOGLE`은 `idToken`, `APPLE`은 1회용 `authorizationCode`(약 5분 만료)다.
      - `email`·`name`은 최초 로그인에서만 캡처하고 재로그인 요청 값은 무시한다.


      **응답 주의사항**

      - 응답 `status`가 클라이언트 재개 지점을 정한다 — `PENDING`은 약관 동의, `TERMS_AGREED`는 온보딩, `ACTIVE`는 홈이다.
      - 온보딩 미완료(`PENDING`·`TERMS_AGREED`) 응답은 `refreshToken`이 null이고 `accessToken`으로 쓸 수 있는 API가 온보딩 단계로 제한된다(`expiresIn` 1800). `ACTIVE`는 access+refresh 둘 다 받는다(3600).

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `provider` 누락·빈값이거나 `APPLE`·`GOOGLE` 밖의 값 |
      | 400 | `AUTH_MISSING_CREDENTIAL` | provider별 필수 자격이 누락·빈값 — `GOOGLE`의 `idToken` 또는 `APPLE`의 `authorizationCode`를 보내지 않음 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `AUTH_INVALID_SOCIAL_TOKEN` | Google `idToken`의 서명·`aud`·`iss`·`exp` 검증 실패, Apple 인가코드 교환 실패(만료·재사용 코드), 교환 결과 검증 실패 |
      | 422 | `AUTH_EMAIL_MISMATCH` | 최초 로그인에서 요청 `email`이 토큰의 email 클레임과 불일치 |
      | 422 | `AUTH_EMAIL_REQUIRED` | 최초 로그인에서 토큰 클레임·요청 어느 쪽에도 `email`이 없어 provider 진본 이메일을 확정할 수 없음 |
      | 502 | `UPSTREAM_ERROR` | Apple `/auth/token` 인가코드 교환이 타임아웃·5xx·I/O 오류로 실패 — 자격 문제가 아니므로 401과 달리 그대로 재시도할 수 있다 |
      """;

  public static final String[] SOCIAL_LOGIN_400 = {
    "INVALID_INPUT", "AUTH_MISSING_CREDENTIAL", "MALFORMED_REQUEST"
  };
  public static final String[] SOCIAL_LOGIN_401 = {"AUTH_INVALID_SOCIAL_TOKEN"};
  public static final String[] SOCIAL_LOGIN_422 = {"AUTH_EMAIL_MISMATCH", "AUTH_EMAIL_REQUIRED"};
  public static final String[] SOCIAL_LOGIN_502 = {"UPSTREAM_ERROR"};

  // ── 약관 동의 — POST /api/v1/auth/terms ─────────────────────────────────────

  public static final String TERMS_SUMMARY = "약관 동의";

  public static final String TERMS_DESCRIPTION =
      """
      이용약관·개인정보처리방침·마케팅 동의를 기록하고 `PENDING`을 `TERMS_AGREED`로 전이한다.

      **헤더**

      - `Authorization: Bearer <accessToken>` — 상태가 `PENDING`·`TERMS_AGREED`인 회원의 토큰(소셜 로그인 직후 발급).

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | 필수 동의 2종(`termsOfServiceAgreed`·`privacyPolicyAgreed`) 중 하나라도 누락(null) |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 토큰 누락·위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 |
      | 409 | `AUTH_ONBOARDING_ALREADY_COMPLETED` | 이미 온보딩을 마친(`ACTIVE`) 사용자의 재요청 |
      | 422 | `AUTH_REQUIRED_AGREEMENT_MISSING` | 필수 동의 2종 중 하나라도 `false` |
      """;

  public static final String[] TERMS_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  public static final String[] TERMS_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  public static final String[] TERMS_409 = {"AUTH_ONBOARDING_ALREADY_COMPLETED"};
  public static final String[] TERMS_422 = {"AUTH_REQUIRED_AGREEMENT_MISSING"};

  // ── 이메일 인증번호 발송 — POST /api/v1/auth/email/verification-code ─────────

  public static final String EMAIL_CODE_SUMMARY = "이메일 인증번호 발송";

  public static final String EMAIL_CODE_DESCRIPTION =
      """
      입력한 이메일로 인증번호를 동기 발송한다. 응답 `email`은 마스킹된다(예 `mi***@example.com`).

      **헤더**

      - `Authorization: Bearer <accessToken>` — 상태가 `ACTIVE`인 회원의 토큰(온보딩 완료).

      **요청 주의사항**

      - 발송이 실패하면(메일 provider 장애·타임아웃) 인증번호가 새로 발급되지 않는다 — 502를 받으면 다시 발송해야 인증번호를 받는다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `email` 누락·빈값이거나 이메일 형식 위반 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 토큰 누락·위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 |
      | 403 | `AUTH_ONBOARDING_REQUIRED` | 상태가 `PENDING`·`TERMS_AGREED`인 토큰으로 호출 |
      | 429 | `TOO_MANY_REQUESTS` | 재발송 간격을 채우지 않은 재요청 |
      | 502 | `UPSTREAM_ERROR` | 메일 provider 장애·타임아웃으로 발송 실패 |
      """;

  public static final String[] EMAIL_CODE_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  public static final String[] EMAIL_CODE_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  public static final String[] EMAIL_CODE_403 = {"AUTH_ONBOARDING_REQUIRED"};
  public static final String[] EMAIL_CODE_429 = {"TOO_MANY_REQUESTS"};
  public static final String[] EMAIL_CODE_502 = {"UPSTREAM_ERROR"};

  // ── 이메일 인증번호 확인 — POST /api/v1/auth/email/verify ────────────────────

  public static final String EMAIL_VERIFY_SUMMARY = "이메일 인증번호 확인";

  public static final String EMAIL_VERIFY_DESCRIPTION =
      """
      발송된 인증번호를 확인해 이메일 인증을 완료한다.

      **헤더**

      - `Authorization: Bearer <accessToken>` — 상태가 `ACTIVE`인 회원의 토큰(온보딩 완료).

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `email`·`code` 누락·빈값이거나 `email`이 이메일 형식 위반 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 토큰 누락·위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 |
      | 403 | `AUTH_ONBOARDING_REQUIRED` | 상태가 `PENDING`·`TERMS_AGREED`인 토큰으로 호출 |
      | 422 | `AUTH_EMAIL_VERIFICATION_FAILED` | 인증번호를 받은 적이 없거나 만료됐거나 코드가 틀림 — 어느 쪽인지 구분해 주지 않는다 |
      | 429 | `TOO_MANY_REQUESTS` | 코드 불일치가 시도 상한까지 누적돼 잠김 |
      """;

  public static final String[] EMAIL_VERIFY_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  public static final String[] EMAIL_VERIFY_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  public static final String[] EMAIL_VERIFY_403 = {"AUTH_ONBOARDING_REQUIRED"};
  public static final String[] EMAIL_VERIFY_422 = {"AUTH_EMAIL_VERIFICATION_FAILED"};
  public static final String[] EMAIL_VERIFY_429 = {"TOO_MANY_REQUESTS"};

  // ── 세입자 온보딩 제출 — POST /api/v1/auth/onboarding ────────────────────────

  public static final String ONBOARDING_SUMMARY = "세입자 온보딩 제출";

  public static final String ONBOARDING_DESCRIPTION =
      """
      세입자 필수 프로필을 제출해 `TERMS_AGREED`를 `ACTIVE`로 전이하고, 닉네임 배정·access·refresh 토큰 발급까지 한 번에 끝낸다.

      **헤더**

      - `Authorization: Bearer <accessToken>` — 상태가 `TERMS_AGREED`인 회원의 토큰.

      **요청 주의사항**

      - 이름·이메일은 소셜 로그인 시점에 확정돼 여기서 받지 않는다.


      **응답 주의사항**

      - 응답 `data.user`의 `phoneNumber`는 세입자 미수집이라 값이 null이 아니라 **필드 자체가 생략**된다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | 필수 필드 누락·빈값, `birthDate`가 `YYYY-MM-DD` 형식이 아니거나 미래 날짜, `gender`·`visaType`·`occupation`·`lang` 값이 지원 목록 밖 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 토큰 누락·위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 |
      | 409 | `AUTH_ONBOARDING_ALREADY_COMPLETED` | 이미 온보딩을 완료(`ACTIVE`)한 사용자의 재요청 |
      | 422 | `AUTH_TERMS_AGREEMENT_REQUIRED` | 약관 미동의(`PENDING`) 상태에서 제출 |
      """;

  public static final String[] ONBOARDING_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  public static final String[] ONBOARDING_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  public static final String[] ONBOARDING_409 = {"AUTH_ONBOARDING_ALREADY_COMPLETED"};
  public static final String[] ONBOARDING_422 = {"AUTH_TERMS_AGREEMENT_REQUIRED"};

  // ── 토큰 재발급 — POST /api/v1/auth/reissue ──────────────────────────────────

  public static final String REISSUE_SUMMARY = "토큰 재발급";

  public static final String REISSUE_DESCRIPTION =
      """
      본문의 refresh 토큰으로 access 토큰을 재발급한다. refresh 토큰은 항상 회전한다.

      **헤더**

      - 인증 불필요 — 토큰 없이 호출한다.
      - 만료된 access 토큰이 붙어 있어도 401로 막지 않는다 — 모든 요청에 토큰을 붙이는 클라이언트가 이 호출만 헤더를 벗길 필요가 없다.

      **응답 주의사항**

      - 응답의 새 refresh 토큰으로 반드시 교체한다. 이전 토큰은 즉시 무효가 되고, 다시 쓰면 탈취로 간주해 그 사용자의 모든 세션이 끊긴다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `refreshToken` 누락·빈값 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `AUTH_INVALID_REFRESH_TOKEN` | 제출한 refresh 토큰의 만료·위조·무효화·재사용 탐지 — 넷을 구분하지 않고 이 코드 하나로 응답한다 |
      """;

  public static final String[] REISSUE_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  public static final String[] REISSUE_401 = {"AUTH_INVALID_REFRESH_TOKEN"};

  // ── 로그아웃 — POST /api/v1/auth/logout ──────────────────────────────────────

  public static final String LOGOUT_SUMMARY = "로그아웃";

  public static final String LOGOUT_DESCRIPTION =
      """
      제출한 refresh 토큰을 무효화한다. 이미 무효한 토큰이어도 204다(멱등).

      **헤더**

      - `Authorization: Bearer <accessToken>` — 상태가 `ACTIVE`인 회원의 토큰(온보딩 완료).

      **응답 주의사항**

      - 로그아웃해도 access 토큰은 무효화되지 않는다 — 남은 만료 시간까지 그대로 API가 호출되므로 클라이언트가 직접 지운다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `refreshToken` 누락·빈값 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | access token 누락·위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 |
      | 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 미완료(`PENDING`·`TERMS_AGREED`) 토큰으로 접근 |
      """;

  public static final String[] LOGOUT_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  public static final String[] LOGOUT_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  public static final String[] LOGOUT_403 = {"AUTH_ONBOARDING_REQUIRED"};

  // ── 연락처 인증번호 발송 — POST /api/v1/auth/phone/verification-code ─────────

  public static final String PHONE_CODE_SUMMARY = "연락처 인증번호 발송";

  public static final String PHONE_CODE_DESCRIPTION =
      """
      입력한 휴대폰 번호로 SMS 인증번호를 동기 발송한다. 응답 `phoneNumber`는 마스킹된다(예 `010-****-5678`).

      **헤더**

      - `Authorization: Bearer <accessToken>` — 상태가 `TERMS_AGREED`·`ACTIVE`인 회원의 토큰. 약관 동의 전(`PENDING`)에 호출하면 422다.
      - 온보딩 중에도, 온보딩을 마친 뒤 프로필 연락처를 바꿀 때도 이 엔드포인트를 쓴다.

      **요청 주의사항**

      - 발송이 실패하면(SMS provider 장애·타임아웃) 인증번호가 새로 발급되지 않는다 — 502를 받으면 다시 발송해야 인증번호를 받는다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `phoneNumber` 누락·빈값이거나 휴대폰 번호 형식 위반(하이픈은 선택) |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 토큰 누락·위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 |
      | 422 | `AUTH_TERMS_AGREEMENT_REQUIRED` | 약관 미동의(`PENDING`) 상태에서 호출 |
      | 429 | `TOO_MANY_REQUESTS` | 재발송 간격을 채우지 않은 재요청 |
      | 502 | `UPSTREAM_ERROR` | SMS provider 장애·타임아웃으로 발송 실패 |
      """;

  public static final String[] PHONE_CODE_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  public static final String[] PHONE_CODE_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  public static final String[] PHONE_CODE_422 = {"AUTH_TERMS_AGREEMENT_REQUIRED"};
  public static final String[] PHONE_CODE_429 = {"TOO_MANY_REQUESTS"};
  public static final String[] PHONE_CODE_502 = {"UPSTREAM_ERROR"};

  // ── 연락처 인증번호 확인 — POST /api/v1/auth/phone/verify ────────────────────

  public static final String PHONE_VERIFY_SUMMARY = "연락처 인증번호 확인";

  public static final String PHONE_VERIFY_DESCRIPTION =
      """
      발송된 인증번호를 확인해 연락처 인증을 완료한다. 임대인 온보딩(`POST /api/v1/auth/landlord/onboarding`)과 프로필 연락처 변경(`PATCH /api/v1/users/me`)의 선행 단계다.

      **헤더**

      - `Authorization: Bearer <accessToken>` — 상태와 무관하게 허용한다(`PENDING`·`TERMS_AGREED`·`ACTIVE`). 발송(`POST /api/v1/auth/phone/verification-code`)과 달리 약관 동의를 보지 않지만, 발송을 먼저 해야 확인할 인증번호가 있다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `phoneNumber`·`code` 누락·빈값이거나 `phoneNumber`가 휴대폰 번호 형식 위반 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 토큰 누락·위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 |
      | 422 | `AUTH_PHONE_VERIFICATION_FAILED` | 인증번호를 받은 적이 없거나 만료됐거나 코드가 틀림 — 어느 쪽인지 구분해 주지 않는다 |
      | 429 | `TOO_MANY_REQUESTS` | 코드 불일치가 시도 상한까지 누적돼 잠김 |
      """;

  public static final String[] PHONE_VERIFY_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  public static final String[] PHONE_VERIFY_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  public static final String[] PHONE_VERIFY_422 = {"AUTH_PHONE_VERIFICATION_FAILED"};
  public static final String[] PHONE_VERIFY_429 = {"TOO_MANY_REQUESTS"};

  // ── 사업자등록번호 검증 — POST /api/v1/auth/business/verify ──────────────────

  public static final String BUSINESS_SUMMARY = "사업자등록번호 검증";

  public static final String BUSINESS_DESCRIPTION =
      """
      사업자등록번호를 외부 registry로 검증한다. 검증 결과는 서버에 남지 않고 응답 본문으로만 돌아오므로 필요할 때마다 다시 호출한다. 번호는 마스킹된다(예 `****567890`).

      **헤더**

      - `Authorization: Bearer <accessToken>` — 상태가 `ACTIVE`인 회원의 토큰(온보딩 완료). 임대인 전용이다.

      **요청 주의사항**

      - 임대인 온보딩(`POST /api/v1/auth/landlord/onboarding`)에는 포함되지 않는다 — 온보딩을 마친 임대인이 매물 등록 시점에 따로 호출한다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `businessRegistrationNumber` 누락·빈값이거나 허용 형식 위반 — 외부 호출 전에 거른다 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 토큰 누락·위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 |
      | 403 | `AUTH_ONBOARDING_REQUIRED` | 상태가 `PENDING`·`TERMS_AGREED`인 토큰으로 호출 — 보안 필터가 거른다 |
      | 403 | `FORBIDDEN` | 임대인이 아닌(`userType=TENANT`) 정식 사용자의 요청 |
      | 422 | `AUTH_BUSINESS_NUMBER_VERIFICATION_FAILED` | 외부 registry 조회 결과가 미등록·휴폐업·진위 실패 |
      | 502 | `UPSTREAM_ERROR` | 외부 검증 API 장애·타임아웃 |
      """;

  public static final String[] BUSINESS_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  public static final String[] BUSINESS_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};

  // 403이 둘로 갈린다 — 온보딩 토큰(필터)은 AUTH_ONBOARDING_REQUIRED, 정식 토큰이지만 세입자(서비스)는 FORBIDDEN이다.
  public static final String[] BUSINESS_403 = {"AUTH_ONBOARDING_REQUIRED", "FORBIDDEN"};
  public static final String[] BUSINESS_422 = {"AUTH_BUSINESS_NUMBER_VERIFICATION_FAILED"};
  public static final String[] BUSINESS_502 = {"UPSTREAM_ERROR"};

  // ── 임대인 온보딩 제출 — POST /api/v1/auth/landlord/onboarding ───────────────

  public static final String LANDLORD_ONBOARDING_SUMMARY = "임대인 온보딩 제출";

  public static final String LANDLORD_ONBOARDING_DESCRIPTION =
      """
      연락처·생년월일을 제출해 `TERMS_AGREED`를 `ACTIVE`로 전이하고 `userType`을 `LANDLORD`로 확정한 뒤 access·refresh 토큰을 발급한다.

      **헤더**

      - `Authorization: Bearer <accessToken>` — 상태가 `TERMS_AGREED`인 회원의 토큰.

      **요청 주의사항**

      - `phoneNumber`는 `POST /api/v1/auth/phone/verification-code`·`/verify`로 사전 인증한 번호와 일치해야 한다(약관 검사가 연락처 검사보다 먼저다).
      - 이름·이메일은 소셜 로그인 시점에 확정돼 여기서 받지 않고, 사업자등록번호도 받지 않는다(온보딩 후 `POST /api/v1/auth/business/verify`로 따로 검증).

      **응답 주의사항**

      - 국적·표시 언어는 서버가 `KR`·`ko`로 고정 부여한다.
      - 응답 `data.user`의 세입자 전용 필드(`gender`·`occupation`·`visaType`)는 값이 null이 아니라 **필드 자체가 생략**되고, `phoneNumber`는 마스킹된다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `phoneNumber` 누락·빈값·휴대폰 번호 형식 위반, `birthDate` 누락이거나 `YYYY-MM-DD` 형식이 아니거나 미래 날짜 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 토큰 누락·위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 |
      | 409 | `AUTH_ONBOARDING_ALREADY_COMPLETED` | 이미 온보딩을 완료(`ACTIVE`)한 사용자의 재요청 |
      | 422 | `AUTH_TERMS_AGREEMENT_REQUIRED` | 약관 미동의(`PENDING`) 상태에서 제출 |
      | 422 | `AUTH_PHONE_NOT_VERIFIED` | 제출한 `phoneNumber`가 미인증이거나 사전 인증한 번호와 불일치 |
      """;

  public static final String[] LANDLORD_ONBOARDING_400 = {"INVALID_INPUT", "MALFORMED_REQUEST"};
  public static final String[] LANDLORD_ONBOARDING_401 = {"UNAUTHENTICATED", "TOKEN_EXPIRED"};
  public static final String[] LANDLORD_ONBOARDING_409 = {"AUTH_ONBOARDING_ALREADY_COMPLETED"};
  public static final String[] LANDLORD_ONBOARDING_422 = {
    "AUTH_TERMS_AGREEMENT_REQUIRED", "AUTH_PHONE_NOT_VERIFIED"
  };

  // ---- 성공 응답/요청 필드 기술자 ----

  public static List<FieldDescriptor> socialLoginRequestFields() {
    return List.of(
        enumField("provider", Provider.class, "소셜 제공자(필수). 누락·빈값·허용 외 문자열 모두 400 INVALID_INPUT"),
        optField("idToken", JsonFieldType.STRING, "Google OIDC ID 토큰 — GOOGLE 필수, APPLE 미사용"),
        optField(
            "authorizationCode",
            JsonFieldType.STRING,
            "Apple 1회용 인가코드(약 5분 만료) — APPLE 필수, GOOGLE 미사용"),
        optField(
            "email",
            JsonFieldType.STRING,
            "앱이 네이티브 SDK에서 받은 이메일(선택 — 최초 로그인에서는 사실상 필수, 토큰 email 클레임과 교차 검증). 재로그인 값은 무시한다"),
        optField(
            "name",
            JsonFieldType.STRING,
            "앱이 네이티브 SDK에서 받은 표시 이름(선택 — 최초 로그인에서만 캡처, 검증 없이 신뢰). Apple은 최초 1회만 제공한다"));
  }

  public static List<FieldDescriptor> socialLoginResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field(
            "data.onboardingRequired",
            JsonFieldType.BOOLEAN,
            "온보딩 필요 여부 — 신규·온보딩 미완료는 true, 기존 ACTIVE는 false"),
        enumField(
            "data.status",
            UserStatus.class,
            "사용자 상태 — 클라이언트 재개 지점 분기. 이 응답에는 PENDING·TERMS_AGREED·ACTIVE만 나온다(WITHDRAWN 계정은 로그인되지 않는다)"),
        field("data.email", JsonFieldType.STRING, "사용자 이메일(provider 진본) — 모든 분기에서 프리필용 반환"),
        optField(
            "data.name",
            JsonFieldType.STRING,
            "사용자 이름(단일 name) — 모든 분기에서 프리필용 반환. 아직 캡처된 이름이 없으면 null이다"),
        codeField("data.tokenType", TOKEN_TYPES, "토큰 타입 — 항상 Bearer"),
        field(
            "data.accessToken",
            JsonFieldType.STRING,
            "access 토큰(JWT). 온보딩 미완료면 온보딩 단계 전용이라 쓸 수 있는 API가 제한된다"),
        optField(
            "data.refreshToken", JsonFieldType.STRING, "refresh 토큰(불투명). 온보딩 미완료 응답에서는 null이다"),
        field("data.expiresIn", JsonFieldType.NUMBER, "access 토큰 만료까지 초(온보딩 1800 / 정식 3600)"),
        errorNull());
  }

  public static List<FieldDescriptor> termsRequestFields() {
    return List.of(
        field("termsOfServiceAgreed", JsonFieldType.BOOLEAN, "이용약관 동의(필수). false면 422"),
        field("privacyPolicyAgreed", JsonFieldType.BOOLEAN, "개인정보처리방침 동의(필수). false면 422"),
        optField("marketingAgreed", JsonFieldType.BOOLEAN, "마케팅 수신 동의(선택, 보내지 않으면 false)"));
  }

  public static List<FieldDescriptor> termsResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        enumField("data.status", UserStatus.class, "전이 후 상태 — 이 응답에서는 항상 TERMS_AGREED"),
        field("data.termsOfServiceAgreed", JsonFieldType.BOOLEAN, "이용약관 동의 여부"),
        field("data.privacyPolicyAgreed", JsonFieldType.BOOLEAN, "개인정보처리방침 동의 여부"),
        field("data.marketingAgreed", JsonFieldType.BOOLEAN, "마케팅 수신 동의 여부"),
        field("data.agreedAt", JsonFieldType.STRING, "동의 시각(ISO-8601 UTC)"),
        errorNull());
  }

  public static List<FieldDescriptor> emailCodeRequestFields() {
    return List.of(field("email", JsonFieldType.STRING, "인증번호를 받을 이메일(필수, 이메일 형식)"));
  }

  public static List<FieldDescriptor> emailCodeResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.email", JsonFieldType.STRING, "마스킹된 이메일(예: mi***@example.com)"),
        field("data.expiresIn", JsonFieldType.NUMBER, "인증번호 만료까지 초"),
        errorNull());
  }

  public static List<FieldDescriptor> emailVerifyRequestFields() {
    return List.of(
        field("email", JsonFieldType.STRING, "인증번호를 발송한 이메일과 일치(필수)"),
        field("code", JsonFieldType.STRING, "발송된 인증번호(필수, 빈값 불가)"));
  }

  public static List<FieldDescriptor> emailVerifyResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.email", JsonFieldType.STRING, "마스킹된 이메일"),
        field("data.verified", JsonFieldType.BOOLEAN, "검증 완료 여부 — 성공 응답은 항상 true"),
        errorNull());
  }

  public static List<FieldDescriptor> onboardingRequestFields() {
    return List.of(
        enumField("gender", Gender.class, "성별(필수). 빈값·목록 밖 값은 400 INVALID_INPUT"),
        field("birthDate", JsonFieldType.STRING, "생년월일 YYYY-MM-DD(필수, 과거 날짜만)"),
        codeField(
            "country",
            COUNTRY_CODES,
            "국적 ISO 3166-1 alpha-2 코드(필수). 값 목록을 내려주는 API가 없어 여기 나열한 15개가 지원 코드의 전부다"),
        optEnumField(
            "occupation", Occupation.class, "직업(선택 — 보내지 않거나 null이면 저장하지 않고 프로필 응답에서 필드 자체가 생략된다)"),
        enumField("visaType", VisaType.class, "비자정보(필수). 요청·응답 모두 상수명으로 주고받는다"),
        optCodeField("lang", LANG_CODES, "표시 언어 ISO 639-1 소문자(선택 — 보내지 않으면 설정하지 않고 표시는 en으로 폴백)"));
  }

  public static List<FieldDescriptor> refreshTokenRequestField(String description) {
    return List.of(field("refreshToken", JsonFieldType.STRING, description));
  }

  public static List<FieldDescriptor> reissueResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        codeField("data.tokenType", TOKEN_TYPES, "토큰 타입 — 항상 Bearer"),
        field("data.accessToken", JsonFieldType.STRING, "새 access 토큰(JWT)"),
        field(
            "data.refreshToken",
            JsonFieldType.STRING,
            "새 refresh 토큰. 이 값으로 교체해야 하며 제출한 토큰은 즉시 무효가 된다"),
        field("data.expiresIn", JsonFieldType.NUMBER, "access 토큰 만료까지 초(3600)"),
        errorNull());
  }

  public static List<FieldDescriptor> phoneCodeRequestFields() {
    return List.of(
        field(
            "phoneNumber",
            JsonFieldType.STRING,
            "인증번호를 받을 휴대폰 번호(필수, 빈값 불가 — 하이픈은 선택이며 서버가 숫자만 남겨 정규화한다)"));
  }

  public static List<FieldDescriptor> phoneCodeResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.phoneNumber", JsonFieldType.STRING, "마스킹된 연락처(예: 010-****-5678)"),
        field("data.expiresIn", JsonFieldType.NUMBER, "인증번호 만료까지 초"),
        errorNull());
  }

  public static List<FieldDescriptor> phoneVerifyRequestFields() {
    return List.of(
        field("phoneNumber", JsonFieldType.STRING, "인증번호를 발송한 연락처와 일치(필수)"),
        field("code", JsonFieldType.STRING, "발송된 인증번호(필수, 빈값 불가)"));
  }

  public static List<FieldDescriptor> phoneVerifyResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field("data.phoneNumber", JsonFieldType.STRING, "마스킹된 연락처(예: 010-****-5678)"),
        field(
            "data.verified",
            JsonFieldType.BOOLEAN,
            "검증 완료 여부 — 성공 응답은 항상 true. 인증된 상태는 30분만 유지되므로 그 안에 임대인 온보딩·프로필 연락처 변경을 제출해야 하고, 넘기면 422 AUTH_PHONE_NOT_VERIFIED라 다시 인증해야 한다"),
        errorNull());
  }

  public static List<FieldDescriptor> businessVerifyRequestFields() {
    return List.of(
        field(
            "businessRegistrationNumber",
            JsonFieldType.STRING,
            "사업자등록번호(필수) — 숫자 10자리 또는 하이픈 형식(123-45-67890) 둘 다 허용하며 동일하게 처리된다"));
  }

  public static List<FieldDescriptor> businessVerifyResponseFields() {
    return List.of(
        field("success", JsonFieldType.BOOLEAN, "성공 여부 — 항상 true"),
        field(
            "data.businessRegistrationNumber", JsonFieldType.STRING, "마스킹된 사업자등록번호(예: ****567890)"),
        field("data.verified", JsonFieldType.BOOLEAN, "정상 사업자 검증 완료 여부 — 성공 응답은 항상 true"),
        errorNull());
  }

  public static List<FieldDescriptor> landlordOnboardingRequestFields() {
    return List.of(
        field(
            "phoneNumber",
            JsonFieldType.STRING,
            "사전 SMS 인증된 연락처와 일치(필수, 빈값 불가 — 하이픈 표기 차이는 정규화로 흡수한다). 불일치·미인증은 422"),
        field("birthDate", JsonFieldType.STRING, "생년월일 YYYY-MM-DD(필수, 과거 날짜만 — 형식 위반·미래는 400)"));
  }
}
