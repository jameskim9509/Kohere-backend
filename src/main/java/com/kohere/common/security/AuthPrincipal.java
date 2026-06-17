package com.kohere.common.security;

/**
 * 인증된 요청 주체. 공통 보안 필터가 JWT를 검증한 뒤 {@code SecurityContext}에 주입한다(ADR-0010).
 *
 * <p>모듈은 이 식별자(userId)만 참조한다(모듈 간 식별자 값 참조, ADR-0002). {@code onboardingCompleted}는 토큰의 온보딩 스코프
 * 클레임으로, 권한(ROLE_USER vs ROLE_ONBOARDING) 결정에 쓰인다.
 */
public record AuthPrincipal(Long userId, boolean onboardingCompleted) {}
