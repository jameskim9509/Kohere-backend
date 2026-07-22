package com.kohere.common.security;

import com.kohere.common.exception.ErrorCode;
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
 * <p>토큰이 없거나 위조·형식 오류면 컨텍스트를 비운 채(익명) 다음 단계로 넘긴다 — 차단은 인가 단계({@link SecurityConfig})가 결정한다. 권한은 온보딩
 * 스코프로 매핑한다(완료=ROLE_USER, 미완료=ROLE_ONBOARDING).
 *
 * <p><b>만료 토큰은 이 필터가 직접 401 {@code TOKEN_EXPIRED}로 끊는다(#181)</b>. permitAll인 게스트 허용 경로(퀴즈·생활 팁·v2
 * 진단)에서는 {@code AuthenticationException}이 발생하지 않아 {@link RestAuthenticationEntryPoint}가 돌지 않으므로,
 * EntryPoint에 맡기면 만료된 회원이 조용히 게스트로 강등된다. 그래서 <b>만료는 경로와 무관하게 여기서 끊는 것을 기본</b>으로 하고(EntryPoint와 동일한
 * {@link SecurityErrorResponder} 출력), <b>{@link PublicPaths} 공개 티어만 예외</b>로 통과시킨다 — 재발급 요청에 만료된
 * access 토큰이 실려 와도 막히지 않게 하기 위해서다. 판정 방향이 "공개 경로인가"라 게스트 허용 경로가 늘어도 목록은 그대로이고, 목록에서 빠진 경로는 만료 시
 * 401이 되어 (fail-closed) 조용한 강등이 아니라 눈에 띄는 실패가 된다. 게스트로 통과시키는 것은 <b>토큰 미전송·위조·형식 오류</b>뿐이다.
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
        if (!PublicPaths.matches(request)) {
          // 만료 토큰은 게스트로 강등하지 않고 여기서 401로 끊는다 — permitAll 경로는 EntryPoint가 돌지 않기
          // 때문이다(#181). 공개 티어(로그인·재발급 등)만 예외로 통과시켜 재발급 교착을 막는다.
          SecurityErrorResponder.write(response, ErrorCode.TOKEN_EXPIRED);
          return;
        }
      } catch (JwtException | IllegalArgumentException e) {
        // 위조·형식 오류 → 익명으로 통과(차단은 인가 단계)
      }
    }
    filterChain.doFilter(request, response);
  }
}
