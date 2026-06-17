package com.kohere.auth.domain;

/**
 * Refresh 토큰 상태. {@code ACTIVE}(유효) → {@code ROTATED}(회전으로 폐기) / {@code REVOKED}(로그아웃·탈퇴·재사용 탐지로
 * 무효화). 폐기 토큰은 삭제하지 않고 상태로 보존해 재사용(탈취) 탐지를 가능케 한다(ADR-0006).
 */
public enum RefreshTokenStatus {
  ACTIVE,
  ROTATED,
  REVOKED
}
