package com.kohere.common.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 검증 횡단 보안 필터(ADR-0010). 매 요청의 {@code Authorization: Bearer} access 토큰을 서명·만료·클레임만 검증(무상태, 저장소
 * 무조회)하고 인증 주체를 {@code SecurityContext}에 주입한다.
 *
 * <p>토큰이 없거나 검증 실패면 컨텍스트를 비운 채(익명) 다음 단계로 넘긴다 — 차단은 인가 단계({@link SecurityConfig})가 결정한다. 만료는 요청
 * 속성으로 표시해 EntryPoint가 {@code TOKEN_EXPIRED}로 구분한다. 권한은 온보딩 스코프로 매핑한다(완료=ROLE_USER,
 * 미완료=ROLE_ONBOARDING).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  /** 만료 토큰 표시 요청 속성. EntryPoint가 401 TOKEN_EXPIRED 구분에 사용. */
  public static final String EXPIRED_ATTRIBUTE = "kohere.jwt.expired";

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenService jwtTokenService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      String token = header.substring(BEARER_PREFIX.length());
      try {
        AuthPrincipal principal = jwtTokenService.parse(token);
        String role = principal.onboardingCompleted() ? "ROLE_USER" : "ROLE_ONBOARDING";
        var authentication =
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (ExpiredJwtException e) {
        request.setAttribute(EXPIRED_ATTRIBUTE, Boolean.TRUE);
      } catch (JwtException | IllegalArgumentException e) {
        // 위조·형식 오류 → 익명으로 통과(차단은 인가 단계)
      }
    }
    filterChain.doFilter(request, response);
  }
}
