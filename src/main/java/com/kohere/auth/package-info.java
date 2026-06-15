/**
 * 인증·온보딩 Bounded Context. 소셜 로그인(Apple/Google) {@code idToken} 검증, 서버 자체 JWT(access+refresh) 발급,
 * refresh 토큰 관리(재발급·무효화), 온보딩 전이 트리거를 담당한다.
 *
 * <p>도메인 에러 코드 prefix: {@code AUTH}. 스펙: docs/api/specs/01-auth-onboarding.md (인증 부분:
 * /api/v1/auth).
 *
 * <p>모듈 경계·계층 규칙은 docs/convention/code-style.md §3을 따른다. 공유 커널 {@code common}에만 의존한다.
 *
 * <p>TODO: 사용자 생성/상태 전이(PENDING→ACTIVE)는 user 모듈과 협력한다. 다른 모듈 타입을 직접 import 하지 않고 공개 API·이벤트로
 * 연결한다(예: 온보딩 완료 시 도메인 이벤트 발행 → user 모듈이 구독).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Auth",
    allowedDependencies = {"common"})
package com.kohere.auth;
