package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.Provider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA 리포지토리(내부). 도메인 포트 {@code SocialAccountRepository}의 어댑터가 사용한다. */
interface SocialAccountJpaRepository extends JpaRepository<SocialAccountJpaEntity, Long> {

  Optional<SocialAccountJpaEntity> findByProviderAndProviderUserId(
      Provider provider, String providerUserId);

  void deleteByUserId(Long userId);
}
