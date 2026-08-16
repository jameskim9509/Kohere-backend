package com.kohere.auth.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA 리포지토리(내부). 도메인 포트 {@code LocalAccountRepository}의 어댑터가 사용한다. */
interface LocalAccountJpaRepository extends JpaRepository<LocalAccountJpaEntity, Long> {

  Optional<LocalAccountJpaEntity> findByEmail(String email);

  Optional<LocalAccountJpaEntity> findByUserId(Long userId);

  boolean existsByEmail(String email);

  boolean existsByUserId(Long userId);
}
