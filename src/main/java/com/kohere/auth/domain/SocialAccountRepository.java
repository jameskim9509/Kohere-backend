package com.kohere.auth.domain;

import java.util.Optional;

/** 소셜 자격 매핑 영속 포트. 구현은 infrastructure에 둔다(의존성 역전). 탈퇴 시 사용자별 매핑 삭제로 재가입을 분리한다(ADR-0014). */
public interface SocialAccountRepository {

  Optional<SocialAccount> findByProviderAndProviderUserId(Provider provider, String providerUserId);

  /** userId로 매핑을 조회한다. 탈퇴 시 매핑 삭제 전에 {@code appleRefreshToken}을 읽어 폐기하기 위해 쓴다(ADR-0031 #5). */
  Optional<SocialAccount> findByUserId(Long userId);

  SocialAccount save(SocialAccount socialAccount);

  void deleteByUserId(Long userId);
}
