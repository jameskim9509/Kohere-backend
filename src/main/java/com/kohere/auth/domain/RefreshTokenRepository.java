package com.kohere.auth.domain;

import java.util.Optional;

/**
 * Refresh 토큰 영속 포트. 구현은 infrastructure에 둔다(의존성 역전). 저장소는 Redis(ADR-0006).
 *
 * <p>회전·무효화는 {@link #save(RefreshToken)}로 상태가 바뀐 토큰을 덮어쓴다. 재사용 탐지·탈퇴 시 사용자별 일괄 무효화는 {@link
 * #revokeAllByUserId(Long)}.
 */
public interface RefreshTokenRepository {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  void save(RefreshToken refreshToken);

  void revokeAllByUserId(Long userId);
}
