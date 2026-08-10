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

      인증: 불필요(공개). 요청 본문의 소셜 자격만으로 판정한다.

      - provider별 자격이 다르다 — `GOOGLE`은 `idToken`, `APPLE`은 1회용 `authorizationCode`(약 5분 만료)다.
      - 응답 `status`가 클라이언트 재개 지점을 정한다 — `PENDING`은 약관 동의, `TERMS_AGREED`는 온보딩, `ACTIVE`는 홈이다.
      - 온보딩 미완료 응답은 `refreshToken`이 null이고 `accessToken`이 온보딩 전용 스코프(`expiresIn` 1800)다. `ACTIVE`는 정식 access+refresh(3600)를 받는다.
      - `email`·`name`은 최초 로그인에서만 캡처하고 재로그인 요청 값은 무시한다. `email`은 토큰의 email 클레임과 교차 검증한다(#192).

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `provider` 누락(null) |
      | 400 | `AUTH_MISSING_CREDENTIAL` | provider별 필수 자격이 누락·빈값 — `GOOGLE`의 `idToken` 또는 `APPLE`의 `authorizationCode` 미전송 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없거나 `provider`가 `APPLE`·`GOOGLE` 밖의 문자열 |
      | 401 | `AUTH_INVALID_SOCIAL_TOKEN` | Google `idToken`의 서명·`aud`·`iss`·`exp` 검증 실패, Apple 인가코드 교환 실패(만료·재사용 코드), 교환으로 받은 `id_token` 검증 실패 |
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

      인증: 필수(온보딩 토큰 `ROLE_ONBOARDING`).

      - `marketingAgreed`는 선택이며 미전송이면 false로 기록한다.
      - 응답 `status`는 전이 후 값이라 항상 `TERMS_AGREED`다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | 필수 동의 2종(`termsOfServiceAgreed`·`privacyPolicyAgreed`) 중 하나라도 누락(null) |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 온보딩 토큰 누락·위조 |
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
      입력한 이메일로 인증번호를 동기 발송하고 챌린지를 저장한다. 응답 `email`은 마스킹된다(예 `mi***@example.com`).

      인증: 필수(정식 토큰 — `ACTIVE`·`ROLE_USER`, #192에서 온보딩 단계 전용 → 정식 전용으로 반전).

      - 발송이 실패하면(메일 provider 장애·타임아웃) 챌린지를 저장하지 않는다.
      - `expiresIn`은 인증번호 만료까지의 초다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `email` 누락·빈값이거나 이메일 형식 위반 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 정식 토큰 누락·위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 |
      | 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 토큰(`PENDING`·`TERMS_AGREED`)으로 호출 |
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
      발송된 인증번호를 확인해 이메일을 검증 완료(VERIFIED)로 마킹한다.

      인증: 필수(정식 토큰 — `ACTIVE`·`ROLE_USER`).

      - `email`은 인증번호를 발송한 이메일과 일치해야 한다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `email`·`code` 누락·빈값이거나 `email`이 이메일 형식 위반 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 정식 토큰 누락·위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 |
      | 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 토큰(`PENDING`·`TERMS_AGREED`)으로 호출 |
      | 422 | `AUTH_EMAIL_VERIFICATION_FAILED` | 챌린지 부재·만료·코드 불일치 — 어느 쪽인지 구분해 주지 않는다 |
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
      세입자 필수 프로필을 제출해 `TERMS_AGREED`를 `ACTIVE`로 전이하고, 닉네임 배정·정식 토큰 발급까지 한 번에 끝낸다.

      인증: 필수(온보딩 토큰, 상태 `TERMS_AGREED`).

      - 필수는 `gender`·`birthDate`(과거 날짜만)·`country`·`visaType`, 선택은 `occupation`·`lang`이다.
      - 이름·이메일은 소셜 로그인 시점에 확정돼 여기서 받지 않는다(#192).
      - enum·날짜를 String으로 받아 서버가 파싱한다 — 요청 DTO가 enum 타입이라 값 위반이 `MALFORMED_REQUEST`인 `PATCH /users/me`와 갈리는 지점이다.
      - 이메일 인증 선행 게이트는 폐지됐다 — 약관만 동의하면 곧바로 제출할 수 있다(#192).
      - 응답 `data.user`의 `phoneNumber`는 세입자 미수집이라 필드 자체가 생략된다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | 필수 필드 누락·빈값, `birthDate`가 미래 날짜, `gender`·`visaType`·`occupation`·`lang` 값이 지원 목록 밖 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 온보딩 토큰 누락·위조 |
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

      인증: 불필요(공개 티어). 클라이언트가 모든 요청에 access 토큰을 붙이는 구조라 만료된 access 토큰이 헤더에 실려 와도 401로 막지 않는다(재발급 교착 방지, #181).

      - 제출한 refresh 토큰은 `ROTATED`로 폐기되고 새 refresh 토큰이 함께 내려온다 — 응답의 새 토큰으로 교체해야 다음 재발급이 된다.

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

      인증: 필수(정식 토큰 — `ROLE_USER`).

      - 성공 응답에는 본문이 없다(204).
      - access 토큰은 stateless라 서버가 폐기하지 않는다 — 클라이언트가 버리고, 남은 만료 시간까지는 서명상 유효하다.

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
      입력한 휴대폰 번호로 SMS 인증번호를 동기 발송하고 챌린지를 저장한다. 응답 `phoneNumber`는 마스킹된다(예 `010-****-5678`).

      인증: 필수(임대인 트랙). 온보딩 토큰과 정식 토큰을 <b>둘 다</b> 허용한다 — 온보딩(US-1-10)과 정식 회원의 프로필 연락처 변경(US-1-5)이 같은 엔드포인트를 쓰기 때문이다(ADR-0034 §6·§8).

      - 약관 동의(`TERMS_AGREED`)가 선행돼야 한다.
      - 발송이 실패하면(SMS provider 장애·타임아웃) 챌린지를 저장하지 않는다.
      - `expiresIn`은 인증번호 만료까지의 초다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `phoneNumber` 누락·빈값 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 온보딩·정식 토큰 누락·위조 |
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
      발송된 인증번호를 확인해 연락처를 검증 완료(VERIFIED)로 마킹한다. 임대인 온보딩(§5-2)·프로필 연락처 변경(§9)의 선행 단계다.

      인증: 필수(임대인 트랙). 온보딩 토큰과 정식 토큰을 둘 다 허용한다.

      - `phoneNumber`는 인증번호를 발송한 번호와 일치해야 한다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `phoneNumber`·`code` 누락·빈값 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 온보딩·정식 토큰 누락·위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 |
      | 422 | `AUTH_PHONE_VERIFICATION_FAILED` | 챌린지 부재·만료·코드 불일치 — 어느 쪽인지 구분해 주지 않는다 |
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
      사업자등록번호를 외부 registry로 검증한다. 결과를 저장하지 않는 무상태 검증이라 응답 본문으로만 돌려주며, 번호는 마스킹된다(예 `****567890`).

      인증: 필수(정식 토큰 — `ACTIVE`·`ROLE_USER`, 임대인 전용). 온보딩 흐름이 아니다.

      - 온보딩 제출(§5-2)에는 포함되지 않는다 — 온보딩을 마친 임대인이 매물 등록 시점에 따로 호출한다.
      - 허용 형식은 숫자 10자리와 하이픈 형식(`123-45-67890`) <b>둘 다</b>다(어댑터가 숫자만 정규화해 대조한다).

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `businessRegistrationNumber` 누락·빈값이거나 허용 형식 위반 — 외부 호출 전에 거른다 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 정식 토큰 누락·위조 |
      | 401 | `TOKEN_EXPIRED` | 만료된 access token으로 호출 |
      | 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 토큰(`PENDING`·`TERMS_AGREED`)으로 호출 — 보안 필터가 거른다 |
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
      연락처·생년월일을 제출해 `TERMS_AGREED`를 `ACTIVE`로 전이하고 `userType`을 `LANDLORD`로 확정한 뒤 정식 토큰을 발급한다.

      인증: 필수(온보딩 토큰, 상태 `TERMS_AGREED`).

      - 요청은 `phoneNumber`·`birthDate` 둘뿐이다 — `phoneNumber`는 빈값만 막고(번호 형식 검증은 없다), `birthDate`는 필수이며 과거 날짜만 허용한다.
      - `phoneNumber`는 사전 SMS 인증(§4-1·§4-2)한 번호와 일치해야 한다(약관 검사가 연락처 검사보다 먼저다).
      - 이름·이메일은 소셜 로그인 시점에 확정돼 여기서 받지 않고, 사업자등록번호도 받지 않는다(온보딩 후 별도 검증 §5-1).
      - 국적·표시 언어는 서버가 `KR`·`ko`로 고정 부여한다. 응답 `data.user`의 세입자 전용 필드(`gender`·`occupation`·`visaType`)는 필드 자체가 생략되고 `phoneNumber`는 마스킹된다.

      **에러 코드**

      | status | `error.code` | 발생 조건 |
      |---|---|---|
      | 400 | `INVALID_INPUT` | `phoneNumber` 누락·빈값, `birthDate` 누락이거나 미래 날짜 |
      | 400 | `MALFORMED_REQUEST` | 요청 본문 JSON을 해석할 수 없음 |
      | 401 | `UNAUTHENTICATED` | 온보딩 토큰 누락·위조 |
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
        enumField(
            "provider",
            Provider.class,
            "소셜 제공자(필수). 누락은 400 INVALID_INPUT, 허용 외 문자열은 400 MALFORMED_REQUEST"),
        optField("idToken", JsonFieldType.STRING, "Google OIDC ID 토큰 — GOOGLE 필수, APPLE 미사용"),
        optField(
            "authorizationCode",
            JsonFieldType.STRING,
            "Apple 1회용 인가코드(약 5분 만료) — APPLE 필수, GOOGLE 미사용"),
        optField(
            "email",
            JsonFieldType.STRING,
            "앱이 네이티브 SDK에서 받은 이메일(선택 — 최초 로그인에서는 사실상 필수, 토큰 email 클레임과 교차 검증). 재로그인 값은 무시한다(#192)"),
        optField(
            "name",
            JsonFieldType.STRING,
            "앱이 네이티브 SDK에서 받은 표시 이름(선택 — 최초 로그인에서만 캡처, 검증 없이 신뢰). Apple은 최초 1회만 제공한다(#192)"));
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
        field("data.email", JsonFieldType.STRING, "사용자 이메일(provider 진본) — 모든 분기에서 프리필용 반환(#192)"),
        optField(
            "data.name",
            JsonFieldType.STRING,
            "사용자 이름(단일 name) — 모든 분기에서 프리필용 반환. 아직 캡처된 이름이 없으면 null이다(#192)"),
        codeField("data.tokenType", TOKEN_TYPES, "토큰 타입 — 항상 Bearer"),
        field(
            "data.accessToken",
            JsonFieldType.STRING,
            "access 토큰(JWT). 온보딩 미완료면 온보딩 전용 스코프(ROLE_ONBOARDING)다"),
        optField(
            "data.refreshToken", JsonFieldType.STRING, "refresh 토큰(불투명). 온보딩 미완료 응답에서는 null이다"),
        field("data.expiresIn", JsonFieldType.NUMBER, "access 토큰 만료까지 초(온보딩 1800 / 정식 3600)"),
        errorNull());
  }

  public static List<FieldDescriptor> termsRequestFields() {
    return List.of(
        field("termsOfServiceAgreed", JsonFieldType.BOOLEAN, "이용약관 동의(필수). false면 422"),
        field("privacyPolicyAgreed", JsonFieldType.BOOLEAN, "개인정보처리방침 동의(필수). false면 422"),
        optField("marketingAgreed", JsonFieldType.BOOLEAN, "마케팅 수신 동의(선택, 미전송이면 false)"));
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
            "국적 ISO 3166-1 alpha-2 코드(필수). 값 목록을 내려주는 API가 없어 여기 나열한 15개가 지원 코드의 전부다(countries 참조 시드)"),
        optEnumField(
            "occupation",
            Occupation.class,
            "직업(선택 — 미전송·null이면 저장하지 않고 프로필 응답에서 필드 자체가 생략된다, #187)"),
        enumField("visaType", VisaType.class, "비자정보(필수). API는 상수명, DB 저장은 표시 라벨"),
        optCodeField("lang", LANG_CODES, "표시 언어 ISO 639-1 소문자(선택 — 미전송이면 미설정으로 두고 표시 시 en으로 폴백)"));
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
            "data.refreshToken", JsonFieldType.STRING, "새 refresh 토큰(회전 — 제출한 토큰은 ROTATED로 폐기된다)"),
        field("data.expiresIn", JsonFieldType.NUMBER, "access 토큰 만료까지 초(3600)"),
        errorNull());
  }

  public static List<FieldDescriptor> phoneCodeRequestFields() {
    return List.of(
        field(
            "phoneNumber",
            JsonFieldType.STRING,
            "인증번호를 받을 휴대폰 번호(필수, 빈값 불가 — 번호 형식 자체를 검증하지는 않는다)"));
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
        field("data.verified", JsonFieldType.BOOLEAN, "검증 완료 여부 — 성공 응답은 항상 true"),
        errorNull());
  }

  public static List<FieldDescriptor> businessVerifyRequestFields() {
    return List.of(
        field(
            "businessRegistrationNumber",
            JsonFieldType.STRING,
            "사업자등록번호(필수) — 숫자 10자리 또는 하이픈 형식(123-45-67890) 둘 다 허용한다. 어댑터가 숫자만 정규화해 대조하므로 두 형식이 동일하게 처리된다"));
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
            "사전 SMS 인증된 연락처와 일치(필수, 빈값 불가 — 번호 형식 자체를 검증하지는 않는다). 불일치·미인증은 422"),
        field("birthDate", JsonFieldType.STRING, "생년월일 YYYY-MM-DD(필수, 과거 날짜만 — 미래면 400)"));
  }
}
