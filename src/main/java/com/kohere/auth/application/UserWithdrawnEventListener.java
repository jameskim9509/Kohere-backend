package com.kohere.auth.application;

import com.kohere.auth.domain.AppleAuthClient;
import com.kohere.auth.domain.Provider;
import com.kohere.auth.domain.RefreshTokenRepository;
import com.kohere.auth.domain.SocialAccountRepository;
import com.kohere.user.api.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 회원 탈퇴 이벤트 구독(ADR-0002/0014). user가 발행한 {@link UserWithdrawnEvent}를 받아 auth 소관 정리를 수행한다 — Apple 연동
 * 폐기, social_accounts 매핑 삭제(재가입 분리), 해당 user의 refresh 토큰 일괄 무효화.
 *
 * <p>탈퇴 트랜잭션 내에서 동기 처리한다(운영에서 비동기 분리가 필요하면 {@code @ApplicationModuleListener}로 전환). Apple {@code
 * /auth/revoke}는 매핑 삭제 <b>전에</b> 호출하며(삭제되면 토큰을 못 읽음), <b>best-effort</b>라 어떤 실패도 탈퇴를 막지
 * 않는다(ADR-0031 #5). 로컬 정리(매핑 삭제·refresh 무효화)는 동기·같은 트랜잭션이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserWithdrawnEventListener {

  private final SocialAccountRepository socialAccountRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final AppleAuthClient appleAuthClient;

  @EventListener
  public void onUserWithdrawn(UserWithdrawnEvent event) {
    revokeAppleLinkIfPresent(event.userId());
    socialAccountRepository.deleteByUserId(event.userId());
    refreshTokenRepository.revokeAllByUserId(event.userId());
  }

  /**
   * Apple 연동이면 매핑 삭제 전에 저장된 refresh token으로 {@code /auth/revoke}를 호출해 앱↔Apple ID 연동을 폐기한다(App Store
   * 5.1.1(v)). 멱등(이미 폐기=성공) 처리는 어댑터가 담당하고, 타임아웃·5xx 등 그 외 실패는 WARN 로그만 남기고 탈퇴를 막지 않는다.
   */
  private void revokeAppleLinkIfPresent(long userId) {
    socialAccountRepository
        .findByUserId(userId)
        .filter(account -> account.getProvider() == Provider.APPLE)
        .ifPresent(
            account -> {
              if (!StringUtils.hasText(account.getAppleRefreshToken())) {
                // 마이그레이션 이전/미연동 Apple 사용자 — 폐기할 토큰 없음(다음 로그인 때 백필, ADR-0031 #5)
                log.warn("Apple 토큰 폐기 스킵 — 저장된 refresh token 없음: userId={}", userId);
                return;
              }
              try {
                appleAuthClient.revokeRefreshToken(account.getAppleRefreshToken());
              } catch (RuntimeException e) {
                // best-effort — 폐기 실패가 탈퇴를 롤백/차단하지 않는다(ADR-0014/0031)
                log.warn("Apple 토큰 폐기 실패(best-effort, 탈퇴는 계속): userId={}", userId, e);
              }
            });
  }
}
