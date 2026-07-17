package com.kohere.user.api;

import java.time.Instant;

/**
 * 차단 목록 항목 뷰(해제 UI용). {@code userId}는 차단한 상대, {@code name}은 상대 표시명(세입자 {@code firstName + " " +
 * lastName}, 임대인 {@code firstName}), {@code blockedAt}은 차단 시각(UTC ISO-8601)이다.
 *
 * <p>표시명 노출은 해제 대상 식별을 위해 필요하다(마스킹 수준은 확인 필요 — MVP는 평문). docs/api/specs/01-auth-onboarding.md §11.
 */
public record BlockedUserView(long userId, String name, Instant blockedAt) {}
