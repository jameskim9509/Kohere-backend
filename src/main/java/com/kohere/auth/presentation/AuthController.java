package com.kohere.auth.presentation;

import com.kohere.auth.application.AuthService;
import com.kohere.auth.application.dto.OnboardingResponse;
import com.kohere.auth.application.dto.SocialLoginResponse;
import com.kohere.auth.application.dto.TokenResponse;
import com.kohere.auth.presentation.dto.LogoutRequest;
import com.kohere.auth.presentation.dto.OnboardingRequest;
import com.kohere.auth.presentation.dto.ReissueRequest;
import com.kohere.auth.presentation.dto.SocialLoginRequest;
import com.kohere.common.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * <p>스펙: docs/api/specs/01-auth-onboarding.md (인증 부분: /api/v1/auth).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/social-login")
  public SocialLoginResponse socialLogin(@Valid @RequestBody SocialLoginRequest request) {
    return authService.socialLogin(request);
  }

  @PostMapping("/onboarding")
  public OnboardingResponse onboarding(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody OnboardingRequest request) {
    return authService.onboarding(principal.userId(), request);
  }

  @PostMapping("/reissue")
  public TokenResponse reissue(@Valid @RequestBody ReissueRequest request) {
    return authService.reissue(request);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@Valid @RequestBody LogoutRequest request) {
    authService.logout(request);
  }
}
