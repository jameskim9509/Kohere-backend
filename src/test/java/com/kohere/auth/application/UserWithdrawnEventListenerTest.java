package com.kohere.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kohere.auth.domain.AppleAuthClient;
import com.kohere.auth.domain.AppleUpstreamException;
import com.kohere.auth.domain.LocalAccountRepository;
import com.kohere.auth.domain.Provider;
import com.kohere.auth.domain.RefreshTokenRepository;
import com.kohere.auth.domain.SocialAccount;
import com.kohere.auth.domain.SocialAccountRepository;
import com.kohere.user.api.UserWithdrawnEvent;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserWithdrawnEventListener} 단위 테스트 — Apple 연동 폐기는 매핑 삭제 <b>전에</b> 일어나고(ADR-0031 #5),
 * best-effort라 폐기 실패가 탈퇴(로컬 정리)를 막지 않으며, Apple 토큰이 없으면(Google·미저장) 폐기를 스킵한다.
 *
 * <p>어느 경로로 들어와도 <b>자격증명 두 채널(social_accounts·local_accounts)이 함께</b> 지워지는지도 함께 본다 — 한쪽만 지우면 탈퇴자가
 * 남은 채널로 다시 로그인한다(ADR-0047의 대칭).
 */
@ExtendWith(MockitoExtension.class)
class UserWithdrawnEventListenerTest {

  @Mock private SocialAccountRepository socialAccountRepository;
  @Mock private LocalAccountRepository localAccountRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private AppleAuthClient appleAuthClient;

  private UserWithdrawnEventListener listener;

  @BeforeEach
  void setUp() {
    listener =
        new UserWithdrawnEventListener(
            socialAccountRepository,
            localAccountRepository,
            refreshTokenRepository,
            appleAuthClient);
  }

  @Test
  void appleUser_revokesRefreshTokenBeforeDeletingMapping() {
    when(socialAccountRepository.findByUserId(1L))
        .thenReturn(Optional.of(appleAccount(1L, "apple-rt-1")));

    listener.onUserWithdrawn(new UserWithdrawnEvent(1L));

    // 매핑이 지워지면 토큰을 못 읽으므로 revoke가 deleteByUserId보다 먼저여야 한다
    InOrder inOrder =
        inOrder(
            appleAuthClient,
            socialAccountRepository,
            localAccountRepository,
            refreshTokenRepository);
    inOrder.verify(appleAuthClient).revokeRefreshToken("apple-rt-1");
    inOrder.verify(socialAccountRepository).deleteByUserId(1L);
    inOrder.verify(localAccountRepository).deleteByUserId(1L);
    inOrder.verify(refreshTokenRepository).revokeAllByUserId(1L);
  }

  @Test
  void revokeFailure_doesNotBlockWithdrawal() {
    when(socialAccountRepository.findByUserId(2L))
        .thenReturn(Optional.of(appleAccount(2L, "apple-rt-2")));
    Mockito.doThrow(new AppleUpstreamException(new RuntimeException("timeout")))
        .when(appleAuthClient)
        .revokeRefreshToken("apple-rt-2");

    assertThatCode(() -> listener.onUserWithdrawn(new UserWithdrawnEvent(2L)))
        .doesNotThrowAnyException();

    // 폐기 실패라도 로컬 정리는 계속 — 탈퇴 차단 금지(ADR-0014/0031 #5)
    verify(socialAccountRepository).deleteByUserId(2L);
    verify(localAccountRepository).deleteByUserId(2L);
    verify(refreshTokenRepository).revokeAllByUserId(2L);
  }

  @Test
  void googleUser_skipsRevoke() {
    when(socialAccountRepository.findByUserId(3L)).thenReturn(Optional.of(googleAccount(3L)));

    listener.onUserWithdrawn(new UserWithdrawnEvent(3L));

    verify(appleAuthClient, never()).revokeRefreshToken(any());
    verify(socialAccountRepository).deleteByUserId(3L);
    verify(localAccountRepository).deleteByUserId(3L);
    verify(refreshTokenRepository).revokeAllByUserId(3L);
  }

  @Test
  void appleUserWithoutStoredToken_skipsRevokeButStillCleansUp() {
    when(socialAccountRepository.findByUserId(4L)).thenReturn(Optional.of(appleAccount(4L, null)));

    listener.onUserWithdrawn(new UserWithdrawnEvent(4L));

    verify(appleAuthClient, never()).revokeRefreshToken(any());
    verify(socialAccountRepository).deleteByUserId(4L);
    verify(localAccountRepository).deleteByUserId(4L);
    verify(refreshTokenRepository).revokeAllByUserId(4L);
  }

  @Test
  void noSocialAccount_cleansUpWithoutRevoke() {
    when(socialAccountRepository.findByUserId(5L)).thenReturn(Optional.empty());

    listener.onUserWithdrawn(new UserWithdrawnEvent(5L));

    verify(appleAuthClient, never()).revokeRefreshToken(any());
    verify(socialAccountRepository).deleteByUserId(5L);
    verify(localAccountRepository).deleteByUserId(5L);
    verify(refreshTokenRepository).revokeAllByUserId(5L);
  }

  private static SocialAccount appleAccount(long userId, String appleRefreshToken) {
    return SocialAccount.builder()
        .id(1L)
        .provider(Provider.APPLE)
        .providerUserId("apple-sub")
        .email("a@example.com")
        .userId(userId)
        .linkedAt(Instant.now())
        .appleRefreshToken(appleRefreshToken)
        .build();
  }

  private static SocialAccount googleAccount(long userId) {
    return SocialAccount.builder()
        .id(2L)
        .provider(Provider.GOOGLE)
        .providerUserId("google-sub")
        .email("a@example.com")
        .userId(userId)
        .linkedAt(Instant.now())
        .build();
  }
}
