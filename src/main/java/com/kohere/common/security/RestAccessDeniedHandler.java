package com.kohere.common.security;

import com.kohere.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 권한 부족(403) 처리. 온보딩 스코프(ROLE_ONBOARDING) 토큰으로 정식 자원(ROLE_USER)에 접근하면 {@code
 * AUTH_ONBOARDING_REQUIRED}, 그 외 권한 부족은 {@code FORBIDDEN}으로 공통 래퍼 응답한다(ADR-0010 §4·§5).
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    SecurityErrorResponder.write(
        response, isOnboardingScope() ? ErrorCode.AUTH_ONBOARDING_REQUIRED : ErrorCode.FORBIDDEN);
  }

  private boolean isOnboardingScope() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return false;
    }
    boolean hasOnboarding = false;
    boolean hasUser = false;
    for (var authority : authentication.getAuthorities()) {
      if ("ROLE_ONBOARDING".equals(authority.getAuthority())) {
        hasOnboarding = true;
      } else if ("ROLE_USER".equals(authority.getAuthority())) {
        hasUser = true;
      }
    }
    return hasOnboarding && !hasUser;
  }
}
