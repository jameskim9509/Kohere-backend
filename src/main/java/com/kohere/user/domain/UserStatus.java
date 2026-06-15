package com.kohere.user.domain;

/**
 * 사용자 상태. {@code PENDING}(소셜 검증만 완료) → {@code ACTIVE}(온보딩 완료) → {@code WITHDRAWN}(탈퇴)로 전이한다.
 * docs/api/specs/01-auth-onboarding.md (status).
 */
public enum UserStatus {
  PENDING,
  ACTIVE,
  WITHDRAWN
}
