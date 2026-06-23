package com.kohere.auth.application;

import com.kohere.auth.application.dto.EmailVerificationCodeResponse;
import com.kohere.auth.application.dto.EmailVerifyResponse;
import com.kohere.auth.application.dto.OnboardingResponse;
import com.kohere.auth.application.dto.SocialLoginResponse;
import com.kohere.auth.application.dto.TermsResponse;
import com.kohere.auth.application.dto.TokenResponse;
import com.kohere.auth.domain.InvalidRefreshTokenException;
import com.kohere.auth.domain.OidcTokenVerifier;
import com.kohere.auth.domain.OidcUser;
import com.kohere.auth.domain.OnboardingAlreadyCompletedException;
import com.kohere.auth.domain.RefreshToken;
import com.kohere.auth.domain.RefreshTokenHasher;
import com.kohere.auth.domain.RefreshTokenRepository;
import com.kohere.auth.domain.RefreshTokenStatus;
import com.kohere.auth.domain.RequiredAgreementMissingException;
import com.kohere.auth.domain.SocialAccount;
import com.kohere.auth.domain.SocialAccountRepository;
import com.kohere.auth.domain.TermsAgreementRequiredException;
import com.kohere.auth.presentation.dto.EmailVerificationCodeRequest;
import com.kohere.auth.presentation.dto.EmailVerifyRequest;
import com.kohere.auth.presentation.dto.LogoutRequest;
import com.kohere.auth.presentation.dto.OnboardingRequest;
import com.kohere.auth.presentation.dto.ReissueRequest;
import com.kohere.auth.presentation.dto.SocialLoginRequest;
import com.kohere.auth.presentation.dto.TermsRequest;
import com.kohere.common.security.JwtTokenService;
import com.kohere.user.api.OnboardingProfile;
import com.kohere.user.api.TermsAgreementView;
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
 * 인증·온보딩 유스케이스 조율(ADR-0003/0006/0010/0011). 소셜 OIDC 검증·서버 JWT 발급·refresh 회전/재사용 탐지/무효화를 담당하고, 약관
 * 동의·회원 생성·상태 전이는 user 공개 API({@link UserAccountService})와 협력한다(user 내부 타입 비참조). 이메일 인증은 {@link
 * EmailVerificationService}에 위임한다.
 *
 * <p>상태 흐름: 소셜 로그인(PENDING) → 약관 동의(TERMS_AGREED) → 이메일 인증 → 온보딩(ACTIVE). 응답의 {@code status}로
 * 클라이언트가 재개 지점을 분기한다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

  private static final String TOKEN_TYPE = "Bearer";
  private static final String STATUS_ACTIVE = "ACTIVE";
  private static final String STATUS_PENDING = "PENDING";
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final OidcTokenVerifier oidcTokenVerifier;
  private final SocialAccountRepository socialAccountRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final RefreshTokenHasher refreshTokenHasher;
  private final JwtTokenService jwtTokenService;
  private final UserAccountService userAccountService;
  private final EmailVerificationService emailVerificationService;
  private final AuthProperties authProperties;

  /**
   * 소셜 로그인. idToken 검증 후 (기존 ACTIVE)→정식 토큰 / (기존 PENDING·TERMS_AGREED·신규)→온보딩 임시 토큰. 응답 {@code
   * status}로 클라이언트가 재개 지점을 분기한다.
   */
  @Transactional
  public SocialLoginResponse socialLogin(SocialLoginRequest request) {
    OidcUser oidcUser = oidcTokenVerifier.verify(request.provider(), request.idToken());
    Optional<SocialAccount> existing =
        socialAccountRepository.findByProviderAndProviderUserId(
            oidcUser.provider(), oidcUser.subject());

    if (existing.isPresent()) {
      long userId = existing.get().getUserId();
      String status = userAccountService.getAccount(userId).status();
      if (STATUS_ACTIVE.equals(status)) {
        TokenResponse tokens = issueFullTokens(userId);
        return new SocialLoginResponse(
            false,
            STATUS_ACTIVE,
            tokens.tokenType(),
            tokens.accessToken(),
            tokens.refreshToken(),
            tokens.expiresIn());
      }
      // 기존 미완료(PENDING·TERMS_AGREED) 재로그인 → 온보딩 임시 토큰 재발급(신규 행 미생성)
      return onboardingResponse(userId, status);
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
    return onboardingResponse(userId, STATUS_PENDING);
  }

  /** 약관 동의(PENDING→TERMS_AGREED). 필수 약관 미동의는 422. 토큰은 갱신하지 않는다(상태만 전이). */
  @Transactional
  public TermsResponse agreeToTerms(long userId, TermsRequest request) {
    if (!Boolean.TRUE.equals(request.termsOfServiceAgreed())
        || !Boolean.TRUE.equals(request.privacyPolicyAgreed())) {
      throw new RequiredAgreementMissingException();
    }
    TermsAgreementView view =
        userAccountService.agreeToTerms(userId, Boolean.TRUE.equals(request.marketingAgreed()));
    return new TermsResponse(
        view.status(),
        view.termsOfServiceAgreed(),
        view.privacyPolicyAgreed(),
        view.marketingAgreed(),
        view.agreedAt());
  }

  /**
   * 온보딩 중 이메일 인증번호 발송. 약관 동의(TERMS_AGREED)가 선행되어야 하므로 미동의(PENDING)는 422
   * AUTH_TERMS_AGREEMENT_REQUIRED로, 이미 완료(ACTIVE)된 사용자의 요청은 409로 거절한다(spec §3). 동기 발송 성공 시에만 챌린지를
   * 저장한다(발송 실패 502).
   */
  @Transactional(readOnly = true)
  public EmailVerificationCodeResponse sendEmailVerificationCode(
      long userId, EmailVerificationCodeRequest request) {
    assertTermsAgreed(userId);
    long expiresIn = emailVerificationService.sendCode(userId, request.email());
    return new EmailVerificationCodeResponse(maskEmail(request.email()), expiresIn);
  }

  /** 이메일 인증번호 확인. 성공 시 이메일을 검증 완료로 마킹한다. */
  @Transactional(readOnly = true)
  public EmailVerifyResponse verifyEmail(long userId, EmailVerifyRequest request) {
    emailVerificationService.verify(userId, request.email(), request.code());
    return new EmailVerifyResponse(maskEmail(request.email()), true);
  }

  /**
   * 온보딩 완료. 온보딩 흐름 순서(약관 동의 → 이메일 인증)를 강제한다 — 약관 미동의(PENDING)면 이메일 인증 안내보다 먼저 422
   * AUTH_TERMS_AGREEMENT_REQUIRED로, 이미 완료(ACTIVE)면 409로 거절한다. 그 뒤 제출 email의 인증 완료를 확인(미검증·불일치 422
   * AUTH_EMAIL_NOT_VERIFIED)하고 user에 TERMS_AGREED→ACTIVE 전이를 위임한 뒤 정식 토큰을 발급한다.
   */
  @Transactional
  public OnboardingResponse onboarding(long userId, OnboardingRequest request) {
    assertTermsAgreed(userId);
    emailVerificationService.assertVerified(userId, request.email());
    UserProfileView user =
        userAccountService.completeOnboarding(
            userId,
            new OnboardingProfile(
                request.firstName(),
                request.lastName(),
                request.gender(),
                request.birthDate(),
                request.country(),
                request.occupation(),
                request.email(),
                request.visaType()));
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

  /**
   * 이메일 인증·온보딩 선행 게이트 — 약관 동의(TERMS_AGREED)를 마쳐야 진행한다. 약관 미동의(PENDING)면 약관 동의 선행 안내(422
   * AUTH_TERMS_AGREEMENT_REQUIRED), 이미 온보딩 완료(ACTIVE)면 409. 상태 소유자는 user이므로 공개 API로 조회만 한다(판정 책임은
   * 흐름을 조율하는 auth).
   */
  private void assertTermsAgreed(long userId) {
    String status = userAccountService.getAccount(userId).status();
    if (STATUS_ACTIVE.equals(status)) {
      throw new OnboardingAlreadyCompletedException();
    }
    if (STATUS_PENDING.equals(status)) {
      throw new TermsAgreementRequiredException();
    }
  }

  private SocialLoginResponse onboardingResponse(long userId, String status) {
    return new SocialLoginResponse(
        true,
        status,
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

  /** 응답·로그용 이메일 마스킹(예: {@code minh@example.com} → {@code mi***@example.com}). */
  private static String maskEmail(String email) {
    if (email == null) {
      return null;
    }
    int at = email.indexOf('@');
    if (at <= 0) {
      return "***";
    }
    String local = email.substring(0, at);
    String domain = email.substring(at);
    String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
    return visible + "***" + domain;
  }
}
