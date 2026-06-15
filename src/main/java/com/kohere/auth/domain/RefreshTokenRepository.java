package com.kohere.auth.domain;

import java.util.Optional;

/**
 * Refresh 토큰 영속 포트. 구현은 infrastructure 계층에 두어 의존성을 역전한다(docs/convention/code-style.md §3-3). 도메인은
 * 영속 기술을 모른다.
 *
 * <p>TODO: 사용자별 토큰 일괄 무효화(탈퇴·재사용 탐지) 메서드를 추가한다.
 */
public interface RefreshTokenRepository {

  Optional<RefreshToken> findByToken(String token);
}
