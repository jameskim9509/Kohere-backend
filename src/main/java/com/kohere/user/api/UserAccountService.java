package com.kohere.user.api;

/**
 * 회원 계정 공개 명령·쿼리. auth가 소셜 로그인/온보딩 흐름에서 동기로 호출한다(공개 API 협력, ADR-0002). user가 User 애그리거트·상태를 소유하고
 * auth는 식별자(userId)만 참조한다.
 */
public interface UserAccountService {

  /** 신규 회원을 PENDING으로 생성하고 식별자를 반환한다(소셜 로그인 신규 분기). */
  long createPendingUser();

  /**
   * 온보딩 완료 — PENDING→ACTIVE 전이 + 프로필·동의·약관 버전 확정.
   *
   * @throws com.kohere.user.domain.OnboardingAlreadyCompletedException 이미 ACTIVE인 경우(409)
   * @throws com.kohere.common.exception.InvalidInputException gender·visaType 값이 유효하지 않은 경우(400)
   */
  UserAccountView completeOnboarding(long userId, OnboardingProfile profile);

  /**
   * 계정 식별·상태 조회(소셜 로그인 분기 판정용).
   *
   * @throws com.kohere.user.domain.UserNotFoundException 없거나 탈퇴한 경우
   */
  UserAccountView getAccount(long userId);
}
