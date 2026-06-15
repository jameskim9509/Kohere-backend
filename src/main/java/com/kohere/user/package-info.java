/**
 * 회원 프로필·계정 lifecycle Bounded Context. 내 프로필 조회/수정, 회원 탈퇴(상태 전이)를 담당한다. 인증 토큰 발급은 auth 모듈의 책임이다.
 *
 * <p>도메인 에러 코드 prefix: {@code USER}. 스펙: docs/api/specs/01-auth-onboarding.md (회원/프로필 부분: {@code
 * /api/v1/users/me}).
 *
 * <p>모듈 경계·계층 규칙은 docs/convention/code-style.md §3을 따른다. 공유 커널 {@code common}에만 의존한다. 온보딩/탈퇴로 인한
 * 사용자 생성·상태 전이는 auth와 협력하지만, 스켈레톤 단계에서는 user가 {@code User} 애그리거트를 소유한다고 보고 자체 완결로 둔다.
 *
 * <p>TODO: auth 모듈과의 협력(소셜 검증 후 PENDING 사용자 생성, 온보딩 완료 ACTIVE 전이, 탈퇴 시 토큰 일괄 무효화)은 추후 공개
 * API·이벤트(Application Events)로 연결한다. 다른 모듈 타입을 직접 import 하지 않는다.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "User",
    allowedDependencies = {"common"})
package com.kohere.user;
