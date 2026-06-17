package com.kohere.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kohere.auth.application.dto.SocialLoginResponse;
import com.kohere.auth.application.dto.TokenResponse;
import com.kohere.auth.domain.InvalidRefreshTokenException;
import com.kohere.auth.domain.OidcTokenVerifier;
import com.kohere.auth.domain.OidcUser;
import com.kohere.auth.domain.Provider;
import com.kohere.auth.domain.RefreshToken;
import com.kohere.auth.domain.RefreshTokenHasher;
import com.kohere.auth.domain.RefreshTokenRepository;
import com.kohere.auth.domain.RequiredAgreementMissingException;
import com.kohere.auth.domain.SocialAccount;
import com.kohere.auth.domain.SocialAccountRepository;
import com.kohere.auth.presentation.dto.LogoutRequest;
import com.kohere.auth.presentation.dto.OnboardingRequest;
import com.kohere.auth.presentation.dto.ReissueRequest;
import com.kohere.auth.presentation.dto.SocialLoginRequest;
import com.kohere.common.security.JwtTokenService;
import com.kohere.user.api.OnboardingProfile;
import com.kohere.user.api.UserAccountService;
import com.kohere.user.api.UserAccountView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AuthService} 단위 테스트(Mockito) — 소셜 로그인 분기(신규/ACTIVE/PENDING), 온보딩(약관 검증), 재발급(항상 회전·재사용
 * 탐지), 로그아웃 멱등. 도메인 포트·user 공개 API를 모킹한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private OidcTokenVerifier oidcTokenVerifier;
  @Mock private SocialAccountRepository socialAccountRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private RefreshTokenHasher refreshTokenHasher;
  @Mock private JwtTokenService jwtTokenService;
  @Mock private UserAccountService userAccountService;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    AuthProperties authProperties = new AuthProperties();
    authProperties.setRefreshTtlSeconds(1209600);
    authProperties.setRefreshPepper("pepper");
    authService =
        new AuthService(
            oidcTokenVerifier,
            socialAccountRepository,
            refreshTokenRepository,
            refreshTokenHasher,
            jwtTokenService,
            userAccountService,
            authProperties);
  }

  @Test
  void socialLogin_newUser_createsPendingAndIssuesOnboardingToken() {
    when(oidcTokenVerifier.verify(Provider.GOOGLE, "idtok"))
        .thenReturn(new OidcUser(Provider.GOOGLE, "sub-1", "a@example.com"));
    when(socialAccountRepository.findByProviderAndProviderUserId(Provider.GOOGLE, "sub-1"))
        .thenReturn(Optional.empty());
    when(userAccountService.createPendingUser()).thenReturn(10L);
    when(jwtTokenService.issueOnboardingToken(10L)).thenReturn("onboarding-token");
    when(jwtTokenService.onboardingTtlSeconds()).thenReturn(1800L);

    SocialLoginResponse response =
        authService.socialLogin(new SocialLoginRequest(Provider.GOOGLE, "idtok"));

    assertThat(response.onboardingRequired()).isTrue();
    assertThat(response.accessToken()).isEqualTo("onboarding-token");
    assertThat(response.refreshToken()).isNull();
    assertThat(response.expiresIn()).isEqualTo(1800L);
    verify(socialAccountRepository).save(any(SocialAccount.class));
    verify(refreshTokenRepository, never()).save(any());
  }

  @Test
  void socialLogin_existingActiveUser_issuesAccessAndRefresh() {
    when(oidcTokenVerifier.verify(Provider.GOOGLE, "idtok"))
        .thenReturn(new OidcUser(Provider.GOOGLE, "sub-1", "a@example.com"));
    when(socialAccountRepository.findByProviderAndProviderUserId(Provider.GOOGLE, "sub-1"))
        .thenReturn(Optional.of(socialAccount(20L)));
    when(userAccountService.getAccount(20L)).thenReturn(new UserAccountView(20L, "ACTIVE"));
    when(jwtTokenService.issueAccessToken(20L)).thenReturn("access-token");
    when(jwtTokenService.accessTtlSeconds()).thenReturn(3600L);
    when(refreshTokenHasher.hash(any())).thenReturn("hash");

    SocialLoginResponse response =
        authService.socialLogin(new SocialLoginRequest(Provider.GOOGLE, "idtok"));

    assertThat(response.onboardingRequired()).isFalse();
    assertThat(response.accessToken()).isEqualTo("access-token");
    assertThat(response.refreshToken()).isNotNull();
    assertThat(response.expiresIn()).isEqualTo(3600L);
    verify(refreshTokenRepository).save(any(RefreshToken.class));
    verify(userAccountService, never()).createPendingUser();
  }

  @Test
  void socialLogin_existingPendingUser_reissuesOnboardingTokenWithoutNewRow() {
    when(oidcTokenVerifier.verify(Provider.GOOGLE, "idtok"))
        .thenReturn(new OidcUser(Provider.GOOGLE, "sub-1", "a@example.com"));
    when(socialAccountRepository.findByProviderAndProviderUserId(Provider.GOOGLE, "sub-1"))
        .thenReturn(Optional.of(socialAccount(30L)));
    when(userAccountService.getAccount(30L)).thenReturn(new UserAccountView(30L, "PENDING"));
    when(jwtTokenService.issueOnboardingToken(30L)).thenReturn("onboarding-token");
    when(jwtTokenService.onboardingTtlSeconds()).thenReturn(1800L);

    SocialLoginResponse response =
        authService.socialLogin(new SocialLoginRequest(Provider.GOOGLE, "idtok"));

    assertThat(response.onboardingRequired()).isTrue();
    assertThat(response.refreshToken()).isNull();
    verify(userAccountService, never()).createPendingUser();
    verify(socialAccountRepository, never()).save(any());
  }

  @Test
  void onboarding_completesAndIssuesFullTokens() {
    when(jwtTokenService.issueAccessToken(40L)).thenReturn("access-token");
    when(jwtTokenService.accessTtlSeconds()).thenReturn(3600L);
    when(refreshTokenHasher.hash(any())).thenReturn("hash");

    TokenResponse response = authService.onboarding(40L, onboardingRequest(true, true));

    assertThat(response.accessToken()).isEqualTo("access-token");
    assertThat(response.refreshToken()).isNotNull();
    verify(userAccountService).completeOnboarding(eq(40L), any(OnboardingProfile.class));
    verify(refreshTokenRepository).save(any(RefreshToken.class));
  }

  @Test
  void onboarding_missingRequiredAgreement_throwsAndDoesNotComplete() {
    assertThatThrownBy(() -> authService.onboarding(40L, onboardingRequest(false, true)))
        .isInstanceOf(RequiredAgreementMissingException.class);

    verify(userAccountService, never()).completeOnboarding(anyLong(), any());
  }

  @Test
  void reissue_rotatesActiveTokenAndIssuesNewTokens() {
    Instant now = Instant.now();
    RefreshToken active = RefreshToken.issue("hash", 50L, now, now.plusSeconds(1000));
    when(refreshTokenHasher.hash("raw-refresh")).thenReturn("hash");
    when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(active));
    when(jwtTokenService.issueAccessToken(50L)).thenReturn("new-access");
    when(jwtTokenService.accessTtlSeconds()).thenReturn(3600L);

    TokenResponse response = authService.reissue(new ReissueRequest("raw-refresh"));

    assertThat(response.accessToken()).isEqualTo("new-access");
    assertThat(response.refreshToken()).isNotNull();
    // 제출 토큰 ROTATED 저장 + 새 ACTIVE 저장 → save 2회
    verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(any(RefreshToken.class));
  }

  @Test
  void reissue_reuseOfRotatedToken_revokesAllAndRejects() {
    Instant now = Instant.now();
    RefreshToken rotated = RefreshToken.issue("hash", 60L, now, now.plusSeconds(1000)).rotate();
    when(refreshTokenHasher.hash("raw-refresh")).thenReturn("hash");
    when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(rotated));

    assertThatThrownBy(() -> authService.reissue(new ReissueRequest("raw-refresh")))
        .isInstanceOf(InvalidRefreshTokenException.class);

    // 회전(ROTATED)된 토큰 재제출 = 재사용 탐지 → 사용자 전 세션 일괄 무효화
    verify(refreshTokenRepository).revokeAllByUserId(60L);
  }

  @Test
  void reissue_revokedToken_rejectsWithoutBulkRevoke() {
    Instant now = Instant.now();
    RefreshToken revoked = RefreshToken.issue("hash", 65L, now, now.plusSeconds(1000)).revoke();
    when(refreshTokenHasher.hash("raw-refresh")).thenReturn("hash");
    when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(revoked));

    assertThatThrownBy(() -> authService.reissue(new ReissueRequest("raw-refresh")))
        .isInstanceOf(InvalidRefreshTokenException.class);

    // 이미 무효화(REVOKED: 로그아웃·탈퇴)된 토큰 재제출은 해당 요청만 거부 — 다른 세션은 보존(일괄 무효화 안 함)
    verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong());
  }

  @Test
  void reissue_unknownToken_rejects() {
    when(refreshTokenHasher.hash("raw-refresh")).thenReturn("hash");
    when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.reissue(new ReissueRequest("raw-refresh")))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void reissue_expiredToken_rejects() {
    Instant now = Instant.now();
    RefreshToken expired =
        RefreshToken.issue("hash", 70L, now.minusSeconds(2000), now.minusSeconds(1000));
    when(refreshTokenHasher.hash("raw-refresh")).thenReturn("hash");
    when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> authService.reissue(new ReissueRequest("raw-refresh")))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void logout_revokesToken() {
    Instant now = Instant.now();
    RefreshToken active = RefreshToken.issue("hash", 80L, now, now.plusSeconds(1000));
    when(refreshTokenHasher.hash("raw-refresh")).thenReturn("hash");
    when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(active));

    authService.logout(new LogoutRequest("raw-refresh"));

    verify(refreshTokenRepository).save(any(RefreshToken.class));
  }

  @Test
  void logout_isIdempotentWhenTokenAbsent() {
    when(refreshTokenHasher.hash("raw-refresh")).thenReturn("hash");
    when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

    authService.logout(new LogoutRequest("raw-refresh"));

    verify(refreshTokenRepository, never()).save(any());
  }

  private static SocialAccount socialAccount(long userId) {
    return SocialAccount.builder()
        .id(1L)
        .provider(Provider.GOOGLE)
        .providerUserId("sub-1")
        .email("a@example.com")
        .userId(userId)
        .linkedAt(Instant.now())
        .build();
  }

  private static OnboardingRequest onboardingRequest(boolean terms, boolean privacy) {
    return new OnboardingRequest(
        "Gil",
        "Hong",
        "MALE",
        LocalDate.of(1990, 1, 1),
        "+82",
        "1012345678",
        "VISA_WORK",
        terms,
        privacy,
        false);
  }
}
