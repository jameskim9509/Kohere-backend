package com.kohere.auth.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * Refresh 토큰 애그리거트 루트. 영속 기술(JPA)에 의존하지 않는 순수 도메인 모델이다. 영속 매핑은 infrastructure 계층의 어댑터에서
 * 처리한다(docs/convention/code-style.md §3-3).
 *
 * <p>docs/api/specs/01-auth-onboarding.md §3·§4 (재발급·로그아웃). TODO: 회전·재사용 탐지 시 무효화 상태 전이 메서드를 채운다.
 */
@Getter
@Builder
public class RefreshToken {

  private final Long id;
  private final Long userId;
  private final String token;
  private final Instant expiresAt;
  private final boolean revoked;
  private final Instant createdAt;
}
