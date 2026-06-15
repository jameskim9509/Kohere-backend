package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.RefreshToken;
import com.kohere.auth.domain.RefreshTokenRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Refresh 토큰 영속 어댑터(스켈레톤 placeholder). 도메인 포트 {@link RefreshTokenRepository}를 구현한다. 현재는 미구현이며 JPA
 * 어댑터로 교체한다(docs/convention/code-style.md §3-3).
 */
@Repository
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {

  @Override
  public Optional<RefreshToken> findByToken(String token) {
    throw new UnsupportedOperationException("TODO: JPA 구현으로 교체");
  }
}
