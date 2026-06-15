package com.kohere.user.infrastructure;

import com.kohere.user.domain.User;
import com.kohere.user.domain.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 회원 영속 어댑터(스켈레톤 placeholder). 도메인 포트 {@link UserRepository}를 구현한다. 현재는 미구현이며 JPA 어댑터로
 * 교체한다(docs/convention/code-style.md §3-3).
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

  @Override
  public Optional<User> findById(Long id) {
    throw new UnsupportedOperationException("TODO: JPA 구현으로 교체");
  }
}
