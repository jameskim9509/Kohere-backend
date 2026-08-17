package com.kohere.auth.application.dto;

/**
 * 임대인 웹 회원가입 결과(US-1-11). 가입은 한 트랜잭션으로 {@code ACTIVE}까지 완주하므로 {@code onboardingRequired}는 항상 {@code
 * false}, {@code status}는 항상 {@code "ACTIVE"}다 — 웹에는 온보딩 재개 화면이 없어 부분 완료 상태를 내려보낼 자리가 없다.
 *
 * <p><b>{@code refreshToken} 필드가 없다.</b> 웹 refresh는 스크립트가 읽을 수 없도록 {@code Set-Cookie}(HttpOnly)로만
 * 내려간다(ADR-0048) — 본문에도 실으면 {@code HttpOnly}로 얻으려던 방어가 그 자리에서 무의미해진다. 쿠키에 담을 원문은 {@link
 * SignupResult}가 프레젠테이션 계층까지만 들고 간다.
 *
 * <p>{@code linked}는 <b>기존 앱 계정에 자격증명만 붙였는지</b>다 — {@code true}면 새 {@code users} 행을 만들지 않았다는 뜻이고, 그
 * 계정으로 웹에서 등록할 매물의 {@code landlordId}가 앱 소셜 로그인의 {@code userId}와 같아진다(이 기능의 존재 이유, ADR-0047).
 *
 * <p>{@code email}·{@code name}은 <b>언제나 {@code users}의 값</b>이다(표시 규칙) — 연동된 계정이면 폼에 적은 웹 이메일이 아니라
 * 소셜 진본 이메일이 나갈 수 있으며 의도된 동작이다. 폼 값은 {@code local_accounts} 스냅샷에만 남는다.
 *
 * <p>docs/api/specs/01-auth-onboarding.md §1-3.
 */
public record SignupResponse(
    boolean linked,
    boolean onboardingRequired,
    String status,
    String tokenType,
    String accessToken,
    long expiresIn,
    String email,
    String name) {}
