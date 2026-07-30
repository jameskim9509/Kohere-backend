package com.kohere.auth.application;

import com.kohere.auth.application.dto.BusinessVerifyResponse;
import com.kohere.auth.application.dto.EmailVerificationCodeResponse;
import com.kohere.auth.application.dto.EmailVerifyResponse;
import com.kohere.auth.application.dto.OnboardingResponse;
import com.kohere.auth.application.dto.PhoneVerificationCodeResponse;
import com.kohere.auth.application.dto.PhoneVerifyResponse;
import com.kohere.auth.application.dto.SocialLoginResponse;
import com.kohere.auth.application.dto.TermsResponse;
import com.kohere.auth.application.dto.TokenResponse;
import com.kohere.auth.domain.AppleAuthClient;
import com.kohere.auth.domain.EmailMismatchException;
import com.kohere.auth.domain.EmailRequiredException;
import com.kohere.auth.domain.InvalidRefreshTokenException;
import com.kohere.auth.domain.LandlordOnlyException;
import com.kohere.auth.domain.MissingCredentialException;
import com.kohere.auth.domain.OidcTokenVerifier;
import com.kohere.auth.domain.OidcUser;
import com.kohere.auth.domain.OnboardingAlreadyCompletedException;
import com.kohere.auth.domain.Provider;
import com.kohere.auth.domain.RefreshToken;
import com.kohere.auth.domain.RefreshTokenHasher;
import com.kohere.auth.domain.RefreshTokenRepository;
import com.kohere.auth.domain.RefreshTokenStatus;
import com.kohere.auth.domain.RequiredAgreementMissingException;
import com.kohere.auth.domain.SocialAccount;
import com.kohere.auth.domain.SocialAccountRepository;
import com.kohere.auth.domain.TermsAgreementRequiredException;
import com.kohere.auth.presentation.dto.BusinessVerifyRequest;
import com.kohere.auth.presentation.dto.EmailVerificationCodeRequest;
import com.kohere.auth.presentation.dto.EmailVerifyRequest;
import com.kohere.auth.presentation.dto.LandlordOnboardingRequest;
import com.kohere.auth.presentation.dto.LogoutRequest;
import com.kohere.auth.presentation.dto.OnboardingRequest;
import com.kohere.auth.presentation.dto.PhoneVerificationCodeRequest;
import com.kohere.auth.presentation.dto.PhoneVerifyRequest;
import com.kohere.auth.presentation.dto.ReissueRequest;
import com.kohere.auth.presentation.dto.SocialLoginRequest;
import com.kohere.auth.presentation.dto.TermsRequest;
import com.kohere.common.security.JwtTokenService;
import com.kohere.user.api.LandlordOnboardingProfile;
import com.kohere.user.api.OnboardingProfile;
import com.kohere.user.api.TermsAgreementView;
import com.kohere.user.api.UserAccountService;
import com.kohere.user.api.UserAccountView;
import com.kohere.user.api.UserProfileView;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 인증·온보딩 유스케이스 조율(ADR-0003/0006/0010/0011). 소셜 OIDC 검증·서버 JWT 발급·refresh 회전/재사용 탐지/무효화를 담당하고, 약관
 * 동의·회원 생성·상태 전이는 user 공개 API({@link UserAccountService})와 협력한다(user 내부 타입 비참조). 이메일 인증은 {@link
 * EmailVerificationService}에 위임한다.
 *
 * <p>상태 흐름: 소셜 로그인(PENDING) → 약관 동의(TERMS_AGREED) → 온보딩(ACTIVE). 응답의 {@code status}로 클라이언트가 재개 지점을
 * 분기한다. 이름·이메일은 소셜 로그인 시점에 provider 값으로 확정하고(#192), 세입자 이메일 인증(§3·§4)은 온보딩 흐름이 아니라 정식(ACTIVE) 사용자
 * 전용이다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

  private static final String TOKEN_TYPE = "Bearer";
  private static final String STATUS_ACTIVE = "ACTIVE";
  private static final String STATUS_PENDING = "PENDING";
  private static final String USER_TYPE_LANDLORD = "LANDLORD";
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final OidcTokenVerifier oidcTokenVerifier;
  private final SocialAccountRepository socialAccountRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final RefreshTokenHasher refreshTokenHasher;
  private final JwtTokenService jwtTokenService;
  private final UserAccountService userAccountService;
  private final EmailVerificationService emailVerificationService;
  private final PhoneVerificationService phoneVerificationService;
  private final BusinessVerificationService businessVerificationService;
  private final AuthProperties authProperties;
  private final AppleAuthClient appleAuthClient;

  /**
   * 소셜 로그인. provider별 자격을 검증해 신원을 얻은 뒤 (기존 ACTIVE)→정식 토큰 / (기존 PENDING·TERMS_AGREED·신규)→온보딩 임시 토큰을
   * 발급한다. <b>Google</b>은 전달받은 {@code idToken}을, <b>Apple</b>은 {@code authorizationCode}를 {@code
   * /auth/token}에서 교환해 받은 {@code id_token}을 검증 대상으로 한다(ADR-0031). 응답 {@code status}로 클라이언트가 재개 지점을
   * 분기한다.
   */
  @Transactional
  public SocialLoginResponse socialLogin(SocialLoginRequest request) {
    ResolvedIdentity identity = resolveIdentity(request);
    OidcUser oidcUser = identity.oidcUser();
    String appleRefreshToken = identity.appleRefreshToken();

    Optional<SocialAccount> existing =
        socialAccountRepository.findByProviderAndProviderUserId(
            oidcUser.provider(), oidcUser.subject());

    if (existing.isPresent()) {
      SocialAccount account = existing.get();
      long userId = account.getUserId();
      // 기존 로그인: User는 건드리지 않고 SocialAccount 스냅샷만 upsert한다(provider 값 변경 반영).
      // email은 토큰 값이 있으면 갱신·없으면 기존값 보존, name은 요청 값이 non-blank일 때만 갱신(없으면 기존값 보존 —
      // Apple 재로그인은 name 미제공), appleRefreshToken은 Apple이 새로 반환했을 때만 갱신(ADR-0031 #4).
      String snapEmail =
          StringUtils.hasText(oidcUser.email()) ? oidcUser.email() : account.getEmail();
      String snapName = StringUtils.hasText(request.name()) ? request.name() : account.getName();
      String snapAppleRefreshToken =
          request.provider() == Provider.APPLE && StringUtils.hasText(appleRefreshToken)
              ? appleRefreshToken
              : account.getAppleRefreshToken();
      socialAccountRepository.save(
          account.toBuilder()
              .email(snapEmail)
              .name(snapName)
              .appleRefreshToken(snapAppleRefreshToken)
              .build());
      UserAccountView acct = userAccountService.getAccount(userId);
      if (STATUS_ACTIVE.equals(acct.status())) {
        TokenResponse tokens = issueFullTokens(userId);
        return new SocialLoginResponse(
            false,
            STATUS_ACTIVE,
            tokens.tokenType(),
            tokens.accessToken(),
            tokens.refreshToken(),
            tokens.expiresIn(),
            acct.email(),
            acct.name());
      }
      // 기존 미완료(PENDING·TERMS_AGREED) 재로그인 → 온보딩 임시 토큰 재발급(신규 행 미생성)
      return onboardingResponse(userId, acct.status(), acct.email(), acct.name());
    }

    // 신규(최초 로그인): email 교차 검증 후 provider 진본으로 확정한다. name은 검증하지 않고 요청 값을 신뢰한다(#192).
    String tokenEmail = oidcUser.email();
    String reqEmail = request.email();
    if (StringUtils.hasText(tokenEmail)
        && StringUtils.hasText(reqEmail)
        && !tokenEmail.equalsIgnoreCase(reqEmail)) {
      throw new EmailMismatchException();
    }
    String email = StringUtils.hasText(tokenEmail) ? tokenEmail : reqEmail;
    if (!StringUtils.hasText(email)) {
      throw new EmailRequiredException();
    }
    // user PENDING 회원 생성(이름·이메일 즉시 세팅) + social_accounts 매핑 생성
    long userId = userAccountService.createPendingUser(request.name(), email);
    socialAccountRepository.save(
        SocialAccount.builder()
            .provider(oidcUser.provider())
            .providerUserId(oidcUser.subject())
            .email(email)
            .name(request.name())
            .userId(userId)
            .linkedAt(Instant.now())
            .appleRefreshToken(appleRefreshToken)
            .build());
    return onboardingResponse(userId, STATUS_PENDING, email, request.name());
  }

  /**
   * provider별 자격을 검증해 신원(과 Apple refresh token)을 얻는다. Google은 전달받은 {@code idToken}을 그대로 검증하고,
   * Apple은 {@code authorizationCode}를 {@code /auth/token}에서 교환해 받은 {@code id_token}을 동일 {@link
   * OidcTokenVerifier}로 재검증한다 — 교환 응답을 맹신하지 않는다(ADR-0031 #1). provider별 필수 자격 누락은 400 {@code
   * AUTH_MISSING_CREDENTIAL}.
   */
  private ResolvedIdentity resolveIdentity(SocialLoginRequest request) {
    return switch (request.provider()) {
      case GOOGLE -> {
        requireCredential(request.idToken());
        yield new ResolvedIdentity(
            oidcTokenVerifier.verify(Provider.GOOGLE, request.idToken()), null);
      }
      case APPLE -> {
        requireCredential(request.authorizationCode());
        // 인가코드는 1회용(약 5분)이라 즉시 교환한다. 교환은 HTTP라 롤백되지 않는 반면 받아온 refresh token은
        // 커밋 전까지 메모리에만 있어, 이후 단계가 실패하면 소모된 인가코드만 남고 토큰은 사라진다.
        // Apple은 재로그인마다 새 인가코드로 refresh token을 다시 내려주므로(ADR-0031 #4 — 최초 1회만 내려오는 건
        // refresh token이 아니라 email·fullName이다) 다음 로그인에 백필되지만, 그 창 안에 탈퇴하면 /auth/revoke를
        // 스킵한다(skip+WARN, UserWithdrawnEventListener). ADR-0031이 수용한 best-effort 범위(durable 재시도
        // 없음)다.
        // 교환을 트랜잭션 밖으로 빼면 외부 호출(connect 3s + read 5s) 동안 DB 커넥션을 잡지 않아 롤백을 부르는
        // 원인(풀 고갈·락 대기·트랜잭션 타임아웃)이 줄어 빈도는 낮아지지만, 토큰이 커밋 전까지 메모리에만 있는 건
        // 같아서 창이 없어지지는 않는다 — 없애려면 토큰을 별도 커밋으로 먼저 저장해야 하는데 신규 가입은 아직 붙일
        // social_accounts 행이 없어 구조 변경이 필요하다.
        AppleAuthClient.AppleTokens tokens =
            appleAuthClient.exchangeAuthorizationCode(request.authorizationCode());
        yield new ResolvedIdentity(
            oidcTokenVerifier.verify(Provider.APPLE, tokens.idToken()), tokens.refreshToken());
      }
    };
  }

  private static void requireCredential(String credential) {
    if (!StringUtils.hasText(credential)) {
      throw new MissingCredentialException();
    }
  }

  /** {@link #resolveIdentity} 결과 — 검증된 신원과 (Apple만) 교환받은 refresh token(없으면 null). */
  private record ResolvedIdentity(OidcUser oidcUser, String appleRefreshToken) {}

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
   * 이메일 인증번호 발송. 정식(ACTIVE) 사용자 전용으로, 접근 제한(정식 토큰·ROLE_USER)은 보안 필터(SecurityConfig)가 담당하므로 여기서는 상태
   * 게이트를 두지 않는다(#192 — 온보딩 단계 전용→정식 전용 반전). 동기 발송 성공 시에만 챌린지를 저장한다(발송 실패 502).
   */
  @Transactional(readOnly = true)
  public EmailVerificationCodeResponse sendEmailVerificationCode(
      long userId, EmailVerificationCodeRequest request) {
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
   * 온보딩 완료. 약관 미동의(PENDING)면 422 AUTH_TERMS_AGREEMENT_REQUIRED로, 이미 완료(ACTIVE)면 409로 거절한다. 이름·이메일은
   * 소셜 로그인 시점에 이미 세팅됐으므로 온보딩에서 받지 않는다(#192 — 이메일 인증 선행 게이트도 제거). user에 TERMS_AGREED→ACTIVE 전이를 위임한
   * 뒤 정식 토큰을 발급한다.
   */
  @Transactional
  public OnboardingResponse onboarding(long userId, OnboardingRequest request) {
    assertTermsAgreed(userId);
    UserProfileView user =
        userAccountService.completeOnboarding(
            userId,
            new OnboardingProfile(
                request.gender(),
                request.birthDate(),
                request.country(),
                request.occupation(),
                request.visaType(),
                request.lang()));
    TokenResponse tokens = issueFullTokens(userId);
    return new OnboardingResponse(
        user, tokens.tokenType(), tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
  }

  /**
   * 연락처 인증번호 발송. 약관 동의(TERMS_AGREED) 이상이면 진행한다 — 온보딩(US-1-10, TERMS_AGREED)과 프로필 연락처 변경(US-1-5,
   * ACTIVE) 두 컨텍스트 모두 허용하고, 약관 미동의(PENDING)만 422 AUTH_TERMS_AGREEMENT_REQUIRED로 거절한다(세입자 이메일 인증 §3과
   * 대칭이나, 정식 토큰 재인증을 허용해 프로필 연락처 변경을 지원 — ADR-0034 §6·§8). 동기 발송 성공 시에만 챌린지를 저장한다(발송 실패 502).
   */
  @Transactional(readOnly = true)
  public PhoneVerificationCodeResponse sendPhoneVerificationCode(
      long userId, PhoneVerificationCodeRequest request) {
    assertPhoneVerificationAllowed(userId);
    long expiresIn = phoneVerificationService.sendCode(userId, request.phoneNumber());
    return new PhoneVerificationCodeResponse(maskPhone(request.phoneNumber()), expiresIn);
  }

  /** 임대인 연락처 인증번호 확인. 성공 시 연락처를 검증 완료로 마킹한다. */
  @Transactional(readOnly = true)
  public PhoneVerifyResponse verifyPhone(long userId, PhoneVerifyRequest request) {
    phoneVerificationService.verify(userId, request.phoneNumber(), request.code());
    return new PhoneVerifyResponse(maskPhone(request.phoneNumber()), true);
  }

  /**
   * 임대인 사업자등록번호 검증(무상태). 온보딩과 분리된 임대인 전용 API로, 온보딩을 마친(ACTIVE) 임대인이 나중에(매물 등록 시점) 호출한다 — 정식
   * 토큰(ACTIVE, ROLE_USER)은 보안 필터가, 임대인(userType=LANDLORD) 여부는 {@link #assertLandlord}가 확인한다(임대인 아님
   * 403 FORBIDDEN). 외부 검증 서비스로 동기 검증해 정상(계속) 사업자면 verified=true를 응답하고(미등록·휴폐업·진위실패 422, 외부 장애 502),
   * 결과는 영속하지 않는다. 시퀀스 US-1-8.
   */
  @Transactional(readOnly = true)
  public BusinessVerifyResponse verifyBusiness(long userId, BusinessVerifyRequest request) {
    assertLandlord(userId);
    businessVerificationService.verify(request.businessRegistrationNumber());
    return new BusinessVerifyResponse(
        maskBusinessNumber(request.businessRegistrationNumber()), true);
  }

  /**
   * 임대인 온보딩 완료. 검증 게이트를 약관 미동의 → 연락처 미인증 우선순위로 통과시킨다 — 약관 미동의(PENDING) 422
   * AUTH_TERMS_AGREEMENT_REQUIRED(이미 ACTIVE면 409), 제출 phoneNumber 미인증·불일치 422
   * AUTH_PHONE_NOT_VERIFIED. 통과 시 user에 TERMS_AGREED→ACTIVE 전이(userType=LANDLORD 확정)를 위임하고 정식 토큰을
   * 발급한다. 사업자등록번호는 온보딩에서 수집하지 않으며, 온보딩 후 별도 검증 API(POST /auth/business/verify)로 검증한다(ADR-0033, 시퀀스
   * US-1-9).
   */
  @Transactional
  public OnboardingResponse landlordOnboarding(long userId, LandlordOnboardingRequest request) {
    assertTermsAgreed(userId);
    phoneVerificationService.assertVerified(userId, request.phoneNumber());
    UserProfileView user =
        userAccountService.completeLandlordOnboarding(
            userId, new LandlordOnboardingProfile(request.phoneNumber(), request.birthDate()));
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

  /**
   * 연락처 인증 선행 게이트 — 약관 동의(TERMS_AGREED) 이상이면 통과한다. 온보딩(US-1-10, TERMS_AGREED)과 프로필 연락처 변경(US-1-5,
   * ACTIVE) 두 컨텍스트 모두 허용하고, 약관 미동의(PENDING)만 422 AUTH_TERMS_AGREEMENT_REQUIRED로 막는다 — 세입자 이메일
   * 인증(온보딩 전용, ACTIVE는 409)과 달리 정식 토큰(ACTIVE) 재인증을 허용한다(ADR-0034 §6·§8). 상태 소유자는 user이므로 공개 API로
   * 조회만 한다.
   */
  private void assertPhoneVerificationAllowed(long userId) {
    if (STATUS_PENDING.equals(userAccountService.getAccount(userId).status())) {
      throw new TermsAgreementRequiredException();
    }
  }

  /**
   * 사업자번호 검증 선행 게이트 — 임대인 전용. 정식 토큰(ACTIVE, ROLE_USER)은 보안 필터({@link
   * com.kohere.common.security.SecurityConfig})가 보장하므로, 여기서는 userType이 LANDLORD인지만 확인한다(임대인 아님 403
   * FORBIDDEN). 상태 소유자는 user이므로 공개 API로 조회만 한다(판정 책임은 흐름을 조율하는 auth).
   */
  private void assertLandlord(long userId) {
    if (!USER_TYPE_LANDLORD.equals(userAccountService.getUserType(userId))) {
      throw new LandlordOnlyException();
    }
  }

  private SocialLoginResponse onboardingResponse(
      long userId, String status, String email, String name) {
    return new SocialLoginResponse(
        true,
        status,
        TOKEN_TYPE,
        jwtTokenService.issueOnboardingToken(userId),
        null,
        jwtTokenService.onboardingTtlSeconds(),
        email,
        name);
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

  /** 응답·로그용 연락처 마스킹(예: {@code 01012345678} → {@code 010-****-5678}). */
  private static String maskPhone(String phone) {
    if (phone == null) {
      return null;
    }
    String digits = phone.replaceAll("\\D", "");
    if (digits.length() < 4) {
      return "***";
    }
    String prefix = digits.substring(0, Math.min(3, digits.length() - 4));
    String suffix = digits.substring(digits.length() - 4);
    return prefix + "-****-" + suffix;
  }

  /** 응답·로그용 사업자등록번호 마스킹(예: {@code 1234567890} → {@code ****567890}). */
  private static String maskBusinessNumber(String number) {
    if (number == null) {
      return null;
    }
    String digits = number.replaceAll("\\D", "");
    if (digits.length() <= 6) {
      return "****" + digits;
    }
    return "****" + digits.substring(digits.length() - 6);
  }
}
