package com.kohere.user.domain;

/**
 * 사용자 상태. {@code PENDING}(소셜 검증만 완료) → {@code TERMS_AGREED}(약관 동의 완료) → {@code ACTIVE}(온보딩 완료) →
 * {@code WITHDRAWN}(탈퇴)로 단방향 전이한다. 약관 동의와 온보딩은 분리된 단계다. docs/api/specs/01-auth-onboarding.md
 * (status).
 */
public enum UserStatus {
  PENDING,
  TERMS_AGREED,
  ACTIVE,
  WITHDRAWN
}
