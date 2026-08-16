package com.kohere.auth.presentation;

import com.kohere.auth.application.AuthService;
import com.kohere.auth.application.dto.BusinessVerifyResponse;
import com.kohere.auth.application.dto.EmailVerificationCodeResponse;
import com.kohere.auth.application.dto.EmailVerifyResponse;
import com.kohere.auth.application.dto.OnboardingResponse;
import com.kohere.auth.application.dto.PhoneVerificationCodeResponse;
import com.kohere.auth.application.dto.PhoneVerifyResponse;
import com.kohere.auth.application.dto.SocialLoginResponse;
import com.kohere.auth.application.dto.TermsResponse;
import com.kohere.auth.application.dto.TokenResponse;
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
import com.kohere.common.security.AuthPrincipal;
import com.kohere.common.security.RefreshTokenCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증·온보딩 REST 컨트롤러. 입력 검증·DTO 변환만 담당하고 비즈니스 로직은 응용 계층에 위임한다 (docs/convention/code-style.md §3-3).
 * 도메인 DTO만 반환하고, 공통 래퍼는 {@link com.kohere.common.response.ApiResponseWrapper}가 자동 적용한다(ADR-0013).
 *
 * <p><b>재발급·로그아웃 두 핸들러만 서블릿 요청·응답을 직접 만진다</b> — 브라우저는 refresh를 {@code HttpOnly} 쿠키로 주고받으므로 요청 쿠키에서
 * 토큰을 꺼내고 회전된 값을 {@code Set-Cookie}로 돌려줘야 한다(ADR-0048 §3). 이 둘만 예외인 것은 <b>웹 전용 엔드포인트를 새로 파지 않기로</b>
 * 했기 때문이다 — 회전·재사용 탐지·전체 무효화는 보안 규칙이라 두 벌이 되는 순간 한쪽만 고친 버그가 조용히 살아남는다. 앱·웹이 같은 경로를 쓰고 <b>채널 차이만 이 한
 * 겹에서 흡수</b>한다(웹 전용 진입점인 가입·로그인은 {@link WebAuthController}가 따로 맡는다).
 *
 * <p>그래도 <b>서블릿 타입은 응용 계층으로 넘어가지 않는다</b> — 컨트롤러가 쿠키·본문 중 어느 값을 쓸지 골라 토큰 문자열만 넘기고, {@link
 * AuthService}는 그것이 어디서 왔는지 모른다.
 *
 * <p>스펙: docs/api/specs/01-auth-onboarding.md (인증 부분: /api/v1/auth).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final RefreshTokenCookies refreshTokenCookies;

  @PostMapping("/social-login")
  public SocialLoginResponse socialLogin(@Valid @RequestBody SocialLoginRequest request) {
    return authService.socialLogin(request);
  }

  @PostMapping("/terms")
  public TermsResponse agreeToTerms(
      @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody TermsRequest request) {
    return authService.agreeToTerms(principal.userId(), request);
  }

  @PostMapping("/email/verification-code")
  public EmailVerificationCodeResponse sendEmailVerificationCode(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody EmailVerificationCodeRequest request) {
    return authService.sendEmailVerificationCode(principal.userId(), request);
  }

  @PostMapping("/email/verify")
  public EmailVerifyResponse verifyEmail(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody EmailVerifyRequest request) {
    return authService.verifyEmail(principal.userId(), request);
  }

  @PostMapping("/onboarding")
  public OnboardingResponse onboarding(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody OnboardingRequest request) {
    return authService.onboarding(principal.userId(), request);
  }

  @PostMapping("/phone/verification-code")
  public PhoneVerificationCodeResponse sendPhoneVerificationCode(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody PhoneVerificationCodeRequest request) {
    return authService.sendPhoneVerificationCode(principal.userId(), request);
  }

  @PostMapping("/phone/verify")
  public PhoneVerifyResponse verifyPhone(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody PhoneVerifyRequest request) {
    return authService.verifyPhone(principal.userId(), request);
  }

  @PostMapping("/business/verify")
  public BusinessVerifyResponse verifyBusiness(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody BusinessVerifyRequest request) {
    return authService.verifyBusiness(principal.userId(), request);
  }

  @PostMapping("/landlord/onboarding")
  public OnboardingResponse landlordOnboarding(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody LandlordOnboardingRequest request) {
    return authService.landlordOnboarding(principal.userId(), request);
  }

  /**
   * 토큰 재발급. refresh를 <b>쿠키({@code refreshToken}) 우선 · 요청 본문 fallback</b>으로 읽고 <b>응답도 요청이 온 채널을
   * 따른다</b>(ADR-0048 §3) — 쿠키로 왔으면 회전된 refresh를 다시 {@code Set-Cookie}로만 내리고 본문 필드는 비우며, 본문으로 왔으면(앱)
   * 종전 그대로 본문에 담고 쿠키를 내리지 않는다. 앱 정상 경로가 한 글자도 바뀌지 않아 v2를 파지 않는다.
   *
   * <p>본문은 선택({@code required = false})이다 — 브라우저는 쿠키가 자동으로 실리므로 본문을 아예 보내지 않는 것이 정상 경로다. 그래서
   * {@code @Valid}·{@code @NotBlank}로는 이 값을 강제할 수 없고(본문이 null이면 Bean Validation을 타지 않는다), 값을 어디서도
   * 찾지 못한 요청의 거절은 {@link AuthService}가 400 {@code INVALID_INPUT}({@code
   * errors[].field=refreshToken})으로 낸다.
   */
  @PostMapping("/reissue")
  public TokenResponse reissue(
      @RequestBody(required = false) ReissueRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    String cookieToken = refreshTokenCookies.read(httpRequest).orElse(null);
    String bodyToken = request == null ? null : request.refreshToken();
    TokenResponse tokens = authService.reissue(cookieToken != null ? cookieToken : bodyToken);
    if (cookieToken == null) {
      return tokens;
    }
    // 쿠키 채널 — 회전된 refresh는 쿠키로만 내리고 본문에서는 지운다. 본문에도 실으면 HttpOnly로 막으려던
    // XSS 유출 경로가 그 자리에서 다시 열린다(ADR-0048 §1).
    httpResponse.addHeader(
        HttpHeaders.SET_COOKIE, refreshTokenCookies.build(tokens.refreshToken()).toString());
    return new TokenResponse(tokens.tokenType(), tokens.accessToken(), null, tokens.expiresIn());
  }

  /**
   * 로그아웃. refresh를 읽는 규칙은 재발급과 같고(쿠키 우선 · 본문 fallback), <b>쿠키로 온 요청에는 {@code Max-Age=0} 삭제 쿠키를 함께
   * 내린다</b> — 서버에서만 무효화하면 브라우저에 죽은 쿠키가 남아 다음 재발급이 401로 실패한다. 본문으로 온 요청(앱)에는 쿠키를 내리지 않는다.
   */
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
      @RequestBody(required = false) LogoutRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    String cookieToken = refreshTokenCookies.read(httpRequest).orElse(null);
    String bodyToken = request == null ? null : request.refreshToken();
    authService.logout(cookieToken != null ? cookieToken : bodyToken);
    if (cookieToken != null) {
      httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookies.delete().toString());
    }
  }
}
