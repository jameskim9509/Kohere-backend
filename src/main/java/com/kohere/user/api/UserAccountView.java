package com.kohere.user.api;

/**
 * 계정 식별·상태 조회 결과(모듈 간 전달용). status는 원시 문자열(PENDING/ACTIVE/WITHDRAWN)로 노출해 내부 enum을 공유하지 않는다.
 *
 * <p>{@code name}·{@code email}은 소셜 로그인 응답 프리필용으로 auth가 조회한다(#192) — 사용자 값({@code User.name}·{@code
 * User.email})이며 {@code name}은 아직 없으면 {@code null}이다.
 */
public record UserAccountView(long userId, String status, String name, String email) {}
