package com.kohere.user.api;

/**
 * 회원 탈퇴 도메인 이벤트. user가 탈퇴(WITHDRAWN 전이) 시 발행하고, auth가 구독해 social_accounts 매핑 삭제 + 해당 user refresh
 * 토큰 일괄 무효화를 수행한다(ADR-0002/0014, 단방향).
 */
public record UserWithdrawnEvent(long userId) {}
