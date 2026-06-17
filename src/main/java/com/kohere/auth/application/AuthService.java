package com.kohere.auth.application;

import com.kohere.auth.application.dto.OnboardingResponse;
import com.kohere.auth.application.dto.SocialLoginResponse;
import com.kohere.auth.application.dto.TokenResponse;
import com.kohere.auth.domain.InvalidRefreshTokenException;
import com.kohere.auth.domain.OidcTokenVerifier;
import com.kohere.auth.domain.OidcUser;
import com.kohere.auth.domain.RefreshToken;
import com.kohere.auth.domain.RefreshTokenHasher;
import com.kohere.auth.domain.RefreshTokenRepository;
import com.kohere.auth.domain.RefreshTokenStatus;
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
import com.kohere.user.api.UserProfileView;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증·온보딩 유스케이스 조율(ADR-0003/0006/0010/0011). 소셜 OIDC 검증·서버 JWT 발급·refresh 회전/재사용 탐지/무효화를 담당하고, 회원
 * 생성·상태 전이는 user 공개 API({@link UserAccountService})와 협력한다(user 내부 타입 비참조).
 *
 * <p>도메인 포트(검증·저장소·해시)와 common JWT 서비스만 의존한다(application→domain, 의존성 역전).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

  private static final String TOKEN_TYPE = "Bearer";
  private static final String STATUS_ACTIVE = "ACTIVE";
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final OidcTokenVerifier oidcTokenVerifier;
  private final SocialAccountRepository socialAccountRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final RefreshTokenHasher refreshTokenHasher;
  private final JwtTokenService jwtTokenService;
  private final UserAccountService userAccountService;
  private final AuthProperties authProperties;

  /** 소셜 로그인. idToken 검증 후 (기존 ACTIVE)→정식 토큰 / (기존 PENDING·신규)→온보딩 임시 토큰. */
  @Transactional
  public SocialLoginResponse socialLogin(SocialLoginRequest request) {
    OidcUser oidcUser = oidcTokenVerifier.verify(request.provider(), request.idToken());
    Optional<SocialAccount> existing =
        socialAccountRepository.findByProviderAndProviderUserId(
            oidcUser.provider(), oidcUser.subject());

    if (existing.isPresent()) {
      long userId = existing.get().getUserId();
      if (STATUS_ACTIVE.equals(userAccountService.getAccount(userId).status())) {
        TokenResponse tokens = issueFullTokens(userId);
        return new SocialLoginResponse(
            false,
            tokens.tokenType(),
            tokens.accessToken(),
            tokens.refreshToken(),
            tokens.expiresIn());
      }
      // 기존 PENDING(온보딩 중단) 재로그인 → 온보딩 임시 토큰 재발급(신규 행 미생성)
      return onboardingResponse(userId);
    }

    // 신규: user PENDING 회원 생성 + social_accounts 매핑 생성
    long userId = userAccountService.createPendingUser();
    socialAccountRepository.save(
        SocialAccount.builder()
            .provider(oidcUser.provider())
            .providerUserId(oidcUser.subject())
            .email(oidcUser.email())
            .userId(userId)
            .linkedAt(Instant.now())
            .build());
    return onboardingResponse(userId);
  }

  /** 온보딩 완료. 필수 약관 검증(422) 후 user에 ACTIVE 전이를 위임하고 정식 토큰을 발급한다. */
  @Transactional
  public OnboardingResponse onboarding(long userId, OnboardingRequest request) {
    if (!Boolean.TRUE.equals(request.termsOfServiceAgreed())
        || !Boolean.TRUE.equals(request.privacyPolicyAgreed())) {
      throw new RequiredAgreementMissingException();
    }
    UserProfileView user =
        userAccountService.completeOnboarding(
            userId,
            new OnboardingProfile(
                request.firstName(),
                request.lastName(),
                request.gender(),
                request.birthDate(),
                request.countryCode(),
                request.phoneNumber(),
                request.visaType(),
                Boolean.TRUE.equals(request.marketingAgreed())));
    TokenResponse tokens = issueFullTokens(userId);
    return new OnboardingResponse(
        user, tokens.tokenType(), tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
  }

  /**
   * 재발급. 항상 회전 — 제출 토큰을 ROTATED로 폐기한다. <b>ROTATED 재제출(재사용 탐지)</b>은 탈취 정황이므로 사용자 전 토큰을 일괄 무효화하고,
   * <b>REVOKED(로그아웃·탈퇴)·만료</b>는 권한이 이미 0이라 해당 요청만 거부해 다른 기기 세션을 보존한다(OAuth 2.0 reuse detection).
   */
  @Transactional
  public TokenResponse reissue(ReissueRequest request) {
    String tokenHash = refreshTokenHasher.hash(request.refreshToken());
    RefreshToken token =
        refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(InvalidRefreshTokenException::new);

    // 회전된 토큰 재등장 = 재사용/탈취 정황 → 사용자 전 세션 무효화
    if (token.getStatus() == RefreshTokenStatus.ROTATED) {
      refreshTokenRepository.revokeAllByUserId(token.getUserId());
      throw new InvalidRefreshTokenException();
    }
    // 이미 무효화(REVOKED)·만료 = 이 요청만 거부(다른 세션 보존)
    if (!token.isUsable(Instant.now())) {
      throw new InvalidRefreshTokenException();
    }
    refreshTokenRepository.save(token.rotate());
    return issueFullTokens(token.getUserId());
  }

  /** 로그아웃. 제출 refresh를 REVOKED로 무효화(이미 무효화돼도 멱등). */
  @Transactional
  public void logout(LogoutRequest request) {
    String tokenHash = refreshTokenHasher.hash(request.refreshToken());
    refreshTokenRepository
        .findByTokenHash(tokenHash)
        .ifPresent(token -> refreshTokenRepository.save(token.revoke()));
  }

  private SocialLoginResponse onboardingResponse(long userId) {
    return new SocialLoginResponse(
        true,
        TOKEN_TYPE,
        jwtTokenService.issueOnboardingToken(userId),
        null,
        jwtTokenService.onboardingTtlSeconds());
  }

  private TokenResponse issueFullTokens(long userId) {
    String accessToken = jwtTokenService.issueAccessToken(userId);
    String rawRefresh = generateRefreshToken();
    Instant now = Instant.now();
    refreshTokenRepository.save(
        RefreshToken.issue(
            refreshTokenHasher.hash(rawRefresh),
            userId,
            now,
            now.plusSeconds(authProperties.getRefreshTtlSeconds())));
    return new TokenResponse(
        TOKEN_TYPE, accessToken, rawRefresh, jwtTokenService.accessTtlSeconds());
  }

  private static String generateRefreshToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return "rt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
