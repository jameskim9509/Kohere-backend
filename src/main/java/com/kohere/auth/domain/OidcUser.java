package com.kohere.auth.domain;

/** 검증된 소셜 idToken에서 추출한 신원. subject는 제공자의 안정적 사용자 식별자(OIDC sub), email은 선택(민감정보). */
public record OidcUser(Provider provider, String subject, String email) {}
