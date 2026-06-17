package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.Provider;
import com.kohere.auth.domain.SocialAccount;
import com.kohere.auth.domain.SocialAccountRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 소셜 자격 매핑 영속 어댑터. 도메인 포트 {@link SocialAccountRepository}를 JPA로 구현하고 도메인↔엔티티를 매핑한다(의존성 역전). */
@Repository
@RequiredArgsConstructor
public class SocialAccountRepositoryImpl implements SocialAccountRepository {

  private final SocialAccountJpaRepository jpaRepository;

  @Override
  public Optional<SocialAccount> findByProviderAndProviderUserId(
      Provider provider, String providerUserId) {
    return jpaRepository
        .findByProviderAndProviderUserId(provider, providerUserId)
        .map(SocialAccountRepositoryImpl::toDomain);
  }

  @Override
  public SocialAccount save(SocialAccount socialAccount) {
    return toDomain(jpaRepository.save(toEntity(socialAccount)));
  }

  @Override
  @Transactional
  public void deleteByUserId(Long userId) {
    jpaRepository.deleteByUserId(userId);
  }

  private static SocialAccount toDomain(SocialAccountJpaEntity e) {
    return SocialAccount.builder()
        .id(e.getId())
        .provider(e.getProvider())
        .providerUserId(e.getProviderUserId())
        .email(e.getEmail())
        .userId(e.getUserId())
        .linkedAt(e.getLinkedAt())
        .build();
  }

  private static SocialAccountJpaEntity toEntity(SocialAccount s) {
    return SocialAccountJpaEntity.builder()
        .id(s.getId())
        .provider(s.getProvider())
        .providerUserId(s.getProviderUserId())
        .email(s.getEmail())
        .userId(s.getUserId())
        .linkedAt(s.getLinkedAt())
        .build();
  }
}
