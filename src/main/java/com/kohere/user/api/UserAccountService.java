package com.kohere.user.api;

import java.util.Optional;

/**
 * 회원 계정 공개 명령·쿼리. auth가 소셜 로그인/약관 동의/온보딩 흐름에서 동기로 호출한다(공개 API 협력, ADR-0002). user가 User 애그리거트·상태를
 * 소유하고 auth는 식별자(userId)만 참조한다.
 */
public interface UserAccountService {

  /**
   * 신규 회원을 PENDING으로 생성하고 식별자를 반환한다(소셜 로그인 신규 분기). 소셜 로그인 시점에 provider가 준 이름({@code
   * name})·이메일({@code email})을 즉시 채운다(#192) — 이름은 없으면 {@code null}이다.
   */
  long createPendingUser(String name, String email);

  /**
   * 약관 동의 — PENDING→TERMS_AGREED 전이 + 동의 3종·약관 버전·동의 시각 확정. 필수 약관 동의 검증은 호출자(auth)가 선행한다. 이미
   * TERMS_AGREED면 멱등 처리한다.
   *
   * @throws com.kohere.user.domain.OnboardingAlreadyCompletedException 이미 ACTIVE인 경우(409)
   * @throws com.kohere.user.domain.UserNotFoundException 없거나 탈퇴한 경우
   */
  TermsAgreementView agreeToTerms(long userId, boolean marketingAgreed);

  /**
   * 온보딩 완료 — TERMS_AGREED→ACTIVE 전이 + 프로필 확정 + 닉네임 자동 배정. email 인증 완료 확인은 호출자(auth)가 선행한다.
   *
   * @throws com.kohere.user.domain.TermsAgreementRequiredException 약관 미동의(PENDING)인 경우(422)
   * @throws com.kohere.user.domain.OnboardingAlreadyCompletedException 이미 ACTIVE인 경우(409)
   * @throws com.kohere.common.exception.InvalidInputException gender·occupation·visaType·country 값이
   *     유효하지 않은 경우(400)
   */
  UserProfileView completeOnboarding(long userId, OnboardingProfile profile);

  /**
   * 임대인 온보딩 완료 — TERMS_AGREED→ACTIVE 전이 + 임대인 프로필 확정(name·연락처·생년월일) + userType=LANDLORD 확정 + 닉네임 자동
   * 배정. 연락처 SMS 인증 완료 확인은 호출자(auth)가 선행한다. 사업자등록번호는 온보딩에서 수집하지 않는다 — 온보딩 후 별도 검증 API로
   * 검증한다(ADR-0033·0034).
   *
   * @throws com.kohere.user.domain.TermsAgreementRequiredException 약관 미동의(PENDING)인 경우(422)
   * @throws com.kohere.user.domain.OnboardingAlreadyCompletedException 이미 ACTIVE인 경우(409)
   */
  UserProfileView completeLandlordOnboarding(long userId, LandlordOnboardingProfile profile);

  /**
   * 인증된 휴대폰 번호로 <b>웹 자격증명을 붙일 기존 임대인 계정</b>을 찾는다(웹 회원가입 US-1-11 · 앱 온보딩 병합 US-1-15의 공통 조회). 조건은
   * 정규화된 번호 + {@code status=ACTIVE} + {@code userType=LANDLORD}이며, 찾은 행에 <b>쓰기 잠금(SELECT … FOR
   * UPDATE)</b>을 걸어 호출자 트랜잭션이 끝날 때까지 붙잡는다.
   *
   * <p><b>왜 상태·역할을 명시하는가</b> — 지금은 {@code phone_number}가 채워진 계정이 사실상 ACTIVE 임대인뿐이라 중복 조건이다. 그래도
   * 적는다: 나중에 누군가 다른 경로에서 {@code PENDING} 계정이나 세입자에 번호를 채우면 그 계정에 남의 웹 자격증명이 붙는다. <b>암묵 불변식에 기대지
   * 않는다</b>(ADR-0047 §4).
   *
   * <p><b>왜 비관적 잠금인가</b> — 이 조회는 "없으면 만든다"의 앞단이라 전형적인 check-then-act다. 잠그지 않으면 같은 번호의 웹 가입과 앱 임대인
   * 온보딩이 서로의 커밋 전에 조회해 <b>둘 다 "없음"으로 판정</b>하고 같은 번호의 ACTIVE 계정이 둘 생긴다. 이 저장소에 비관적 잠금 선례가 없어 여기서
   * 관용구를 새로 들이는 것이며, 대상이 항상 <b>단일 행·짧은 트랜잭션</b>이라 잠금 범위가 작다.
   *
   * <p><b>다만 잠금만으로는 부족하다.</b> 행이 <b>없을 때</b>는 잠글 행 자체가 없어(MySQL은 갭 락을 걸지만 REPEATABLE READ + 세컨더리
   * 유니크 인덱스에 의존하는 셈이다) "못 찾음" 결과는 그 자체로 안전하지 않다. <b>최종 방어선은 {@code
   * uq_users_phone_number}(V23)</b>이며 늦은 트랜잭션이 제약 위반으로 실패하고, 재시도하면 상대가 만든 계정을 발견해 정상 연동·병합된다.
   *
   * @param normalizedPhoneNumber 숫자만 남긴 표준형 번호. 구현이 방어적으로 한 번 더 접는다(멱등)
   * @return 매칭된 회원 식별자. 없으면 비어 있음(신규 가입 경로)
   */
  Optional<Long> findActiveLandlordIdByPhoneNumber(String normalizedPhoneNumber);

  /**
   * 계정 식별·상태 조회(소셜 로그인 분기 판정용).
   *
   * @throws com.kohere.user.domain.UserNotFoundException 없거나 탈퇴한 경우
   */
  UserAccountView getAccount(long userId);

  /**
   * 회원 역할(userType — 예 {@code TENANT}·{@code LANDLORD}) 조회. auth가 임대인 전용 자원(사업자번호 검증) 접근 판정에 동기
   * 호출한다.
   *
   * @throws com.kohere.user.domain.UserNotFoundException 없거나 탈퇴한 경우
   */
  String getUserType(long userId);

  /**
   * 표시 언어(ISO 639-1) 조회. diagnosis 등 다국어 표시 모듈이 사용자 언어를 결정하기 위해 동기 호출한다(ADR-0002 Decision 5 — 즉시
   * 결과가 필요한 조회, 토큰 클레임 미사용·ADR-0029). 사용자가 고른 표시 언어({@code users.lang})를 반환하며, 미설정이면(온보딩 전·미선택)
   * 영어({@code en})로 폴백한다(에러 아님, #141 — 국가→언어 도출 폐기).
   *
   * @throws com.kohere.user.domain.UserNotFoundException 없거나 탈퇴한 경우
   */
  String getLanguage(long userId);

  /**
   * 회원 표시 이름 조회. booking 등 다른 모듈이 예약자 성명 표시를 위해 동기 호출한다(ADR-0002 공개 API 협력). 세입자·임대인 공통 단일 {@code
   * name}이며(#192), 이름이 없으면(소셜 로그인 시 provider 미제공·탈퇴 등) 빈 문자열을 반환한다.
   *
   * @throws com.kohere.user.domain.UserNotFoundException 없거나 탈퇴한 경우
   */
  String getUserName(long userId);

  /**
   * 신청자(세입자) 프로필 조회. booking의 임대인 받은 신청 상세 조회(US-4-6, userType 분기)가 신청자 정보를 표시하기 위해 동기
   * 호출한다(ADR-0002 공개 API 협력). 성명(단일 {@code name})·성별·국적(ISO 코드·표시명)·이메일을 담으며, 임대인에게 마스킹 없이 평문
   * 노출한다(제품 결정). 온보딩 전/탈퇴 등으로 값이 없으면 {@code name}은 빈 문자열, 나머지는 {@code null}이다.
   *
   * @throws com.kohere.user.domain.UserNotFoundException 없거나 탈퇴한 경우
   */
  ApplicantProfileView getApplicantProfile(long userId);
}
