package com.kohere.auth.application;

import com.kohere.auth.domain.RefreshTokenRepository;
import com.kohere.auth.domain.SocialAccountRepository;
import com.kohere.user.api.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 이벤트 구독(ADR-0002/0014). user가 발행한 {@link UserWithdrawnEvent}를 받아 auth 소관 정리를 수행한다 —
 * social_accounts 매핑 삭제(재가입 분리)와 해당 user의 refresh 토큰 일괄 무효화.
 *
 * <p>탈퇴 트랜잭션 내에서 동기 처리한다(운영에서 비동기 분리가 필요하면 {@code @ApplicationModuleListener}로 전환).
 */
@Component
@RequiredArgsConstructor
public class UserWithdrawnEventListener {

  private final SocialAccountRepository socialAccountRepository;
  private final RefreshTokenRepository refreshTokenRepository;

  @EventListener
  public void onUserWithdrawn(UserWithdrawnEvent event) {
    socialAccountRepository.deleteByUserId(event.userId());
    refreshTokenRepository.revokeAllByUserId(event.userId());
  }
}
