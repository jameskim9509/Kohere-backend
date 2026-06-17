package com.kohere.auth.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * Refresh 토큰 애그리거트 루트. 식별자는 원문 토큰의 단방향 해시(tokenHash) — 원문은 보관하지 않는다(ADR-0006). 회전·무효화는 삭제가 아닌 상태
 * 전이로 처리해 폐기 토큰을 만료까지 보존(재사용 탐지)한다.
 *
 * <p>순수 도메인 모델이며 저장소(Redis, ADR-0006)는 infrastructure 어댑터가 담당한다.
 */
@Getter
@Builder(toBuilder = true)
public class RefreshToken {

  private final String tokenHash;
  private final Long userId;
  private final RefreshTokenStatus status;
  private final Instant issuedAt;
  private final Instant expiresAt;

  /** 신규 발급(ACTIVE). */
  public static RefreshToken issue(
      String tokenHash, Long userId, Instant issuedAt, Instant expiresAt) {
    return RefreshToken.builder()
        .tokenHash(tokenHash)
        .userId(userId)
        .status(RefreshTokenStatus.ACTIVE)
        .issuedAt(issuedAt)
        .expiresAt(expiresAt)
        .build();
  }

  /** ACTIVE이고 만료 전이어야 사용 가능. */
  public boolean isUsable(Instant now) {
    return status == RefreshTokenStatus.ACTIVE && expiresAt.isAfter(now);
  }

  /** 회전 — 제출 토큰을 폐기(ROTATED)하되 보존(재사용 탐지용). */
  public RefreshToken rotate() {
    return toBuilder().status(RefreshTokenStatus.ROTATED).build();
  }

  /** 무효화(REVOKED) — 로그아웃·탈퇴·재사용 탐지. */
  public RefreshToken revoke() {
    return toBuilder().status(RefreshTokenStatus.REVOKED).build();
  }
}
