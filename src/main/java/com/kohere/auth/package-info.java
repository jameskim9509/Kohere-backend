/**
 * 인증·온보딩 Bounded Context. 소셜 로그인(Google {@code idToken} 검증, Apple authorization-code 교환 후 {@code
 * id_token} 검증, ADR-0031), 서버 자체 JWT(access+refresh) 발급, refresh 토큰 관리(재발급·무효화), 온보딩 전이 트리거를 담당한다.
 *
 * <p>도메인 에러 코드 prefix: {@code AUTH}. 스펙: docs/api/specs/01-auth-onboarding.md (인증 부분:
 * /api/v1/auth).
 *
 * <p>모듈 경계·계층 규칙은 docs/convention/code-style.md §3을 따른다. 공유 커널 {@code common}과 user 공개 API({@code
 * user :: api})에만 의존한다.
 *
 * <p>사용자 생성/상태 전이(PENDING→ACTIVE)는 user 공개 API({@link com.kohere.user.api.UserAccountService})를 동기
 * 호출하고, 탈퇴는 user가 발행하는 {@link com.kohere.user.api.UserWithdrawnEvent}를 구독해 Apple 연동 폐기(best-effort,
 * ADR-0031)·social_accounts 삭제·refresh 무효화로 협력한다(ADR-0002). user 내부 도메인 타입은 import 하지 않는다.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Auth",
    allowedDependencies = {"common", "user :: api"})
package com.kohere.auth;
