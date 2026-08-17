package com.kohere.auth.application.dto;

/**
 * 임대인 웹 로그인 결과(US-1-12). 필드 구성은 {@link SignupResponse}에서 {@code linked}만 뺀 것과 같다 — 가입과 로그인이 같은 화면
 * 전환(매물 관리)으로 이어지므로 클라이언트가 두 응답을 같은 코드로 다룰 수 있게 맞춘다. {@code linked}가 없는 것은 로그인이 계정을 붙이거나 만들지 않기
 * 때문이다.
 *
 * <p>{@code onboardingRequired}는 항상 {@code false}, {@code status}는 항상 {@code "ACTIVE"}다 — 웹 가입이 한
 * 트랜잭션으로 {@code ACTIVE}까지 완주하므로({@link SignupResponse}) 앱처럼 {@code PENDING}·{@code TERMS_AGREED}
 * 상태로 로그인해 재개 지점을 분기하는 경로가 <b>웹에는 존재하지 않는다</b>. 그래도 필드를 내려보내는 이유는 클라이언트가 소셜 로그인 응답과 같은 모양으로 분기 코드를
 * 쓰기 때문이다.
 *
 * <p><b>{@code refreshToken} 필드가 없다.</b> 웹 refresh는 스크립트가 읽을 수 없도록 {@code Set-Cookie}(HttpOnly)로만
 * 내려간다(ADR-0048). 쿠키에 담을 원문은 {@link LoginResult}가 프레젠테이션 계층까지만 들고 간다.
 *
 * <p>{@code email}·{@code name}은 <b>{@code users}의 값</b>이다(표시 규칙) — 로그인 ID는 {@code
 * local_accounts.email}이지만 화면에 보여 줄 정본은 프로필이라, 앱 계정에 연동된 임대인은 <b>로그인에 쓴 주소와 다른 소셜 진본 이메일</b>을 받을 수
 * 있다.
 *
 * <p>docs/api/specs/01-auth-onboarding.md §1-4.
 */
public record LoginResponse(
    boolean onboardingRequired,
    String status,
    String tokenType,
    String accessToken,
    long expiresIn,
    String email,
    String name) {}
