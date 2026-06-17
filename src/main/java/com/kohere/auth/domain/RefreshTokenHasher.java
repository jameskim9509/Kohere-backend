package com.kohere.auth.domain;

/**
 * 불투명 refresh 토큰의 단방향 해시 포트. 구현은 infrastructure에 둔다(의존성 역전). 저장소 식별자(tokenHash)는 원문이 아닌 이 해시값이다
 * (ADR-0006).
 */
public interface RefreshTokenHasher {

  String hash(String rawToken);
}
