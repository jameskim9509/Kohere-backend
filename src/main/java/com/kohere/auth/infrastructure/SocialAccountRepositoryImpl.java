package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.Provider;
import com.kohere.auth.domain.SocialAccount;
import com.kohere.auth.domain.SocialAccountRepository;
import java.util.List;
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
  public List<SocialAccount> findAllByUserId(long userId) {
    return jpaRepository.findAllByUserId(userId).stream()
        .map(SocialAccountRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  public SocialAccount save(SocialAccount socialAccount) {
    return toDomain(jpaRepository.save(toEntity(socialAccount)));
  }

  /**
   * 병합의 매핑 이전. {@code @Transactional}은 벌크 UPDATE가 트랜잭션을 요구하기 때문이며(없으면 {@code
   * TransactionRequiredException}), 실제 호출은 {@code REQUIRED} 전파로 <b>병합 트랜잭션에 참여</b>한다 — 여기서 따로 커밋되면
   * 뒤이은 {@code users} 삭제가 실패했을 때 매핑만 옮겨진 상태가 남아 앱 로그인이 어느 쪽으로도 붙지 않는다.
   */
  @Override
  @Transactional
  public void reassignUserId(long fromUserId, long toUserId) {
    jpaRepository.reassignUserId(fromUserId, toUserId);
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
        .name(e.getName())
        .userId(e.getUserId())
        .linkedAt(e.getLinkedAt())
        .appleRefreshToken(e.getAppleRefreshToken())
        .build();
  }

  private static SocialAccountJpaEntity toEntity(SocialAccount s) {
    return SocialAccountJpaEntity.builder()
        .id(s.getId())
        .provider(s.getProvider())
        .providerUserId(s.getProviderUserId())
        .email(s.getEmail())
        .name(s.getName())
        .userId(s.getUserId())
        .linkedAt(s.getLinkedAt())
        .appleRefreshToken(s.getAppleRefreshToken())
        .build();
  }
}
