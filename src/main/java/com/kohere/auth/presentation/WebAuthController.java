package com.kohere.auth.presentation;

import com.kohere.auth.application.WebAuthService;
import com.kohere.auth.application.dto.LoginResponse;
import com.kohere.auth.application.dto.LoginResult;
import com.kohere.auth.application.dto.SignupResponse;
import com.kohere.auth.application.dto.SignupResult;
import com.kohere.auth.presentation.dto.LoginRequest;
import com.kohere.auth.presentation.dto.SignupRequest;
import com.kohere.common.request.ClientIps;
import com.kohere.common.security.RefreshTokenCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 임대인 웹(로컬 자격증명) 인증 REST 컨트롤러 — 회원가입(US-1-11)·로그인(US-1-12). 입력 검증·DTO 변환과 <b>refresh 쿠키 전송</b>만
 * 담당하고 비즈니스 로직은 응용 계층에 위임한다(docs/convention/code-style.md §3-3). 응답 본문의 공통 래퍼는 {@link
 * com.kohere.common.response.ApiResponseWrapper}가 자동 적용한다(ADR-0013).
 *
 * <p><b>왜 {@link AuthController}에 넣지 않는가</b> — {@code AuthController}는 협력자가 {@code AuthService}
 * 하나이고 모든 핸들러가 "요청 DTO를 받아 응답 DTO를 돌려준다"로 끝나는, 소셜 로그인으로 시작하는 계정 생애주기 컨트롤러다. 웹 트랙은 협력자가 다르고({@link
 * WebAuthService}) 계정이 아직 없는 별개 진입점이며, 무엇보다 <b>응답 헤더({@code Set-Cookie})를 직접 만져야</b> 한다. 서블릿 응답을
 * 다루는 메서드를 그 컨트롤러 한가운데 끼워 넣으면 "입력 변환만 한다"는 성질이 깨지므로 채널 단위로 분리한다(가입용 SMS 인증 {@link
 * SignupPhoneVerificationController}와 같은 판단).
 *
 * <p><b>반환 타입은 그대로 응답 DTO다.</b> {@code ResponseEntity}로 바꿔 헤더를 싣는 대신 {@link HttpServletResponse}를
 * 핸들러 인자로 받는다 — 반환 타입이 바뀌면 자동 래핑({@code ApiResponseWrapper})의 적용 대상에서 벗어나 이 엔드포인트만 공통 래퍼가 빠진다.
 *
 * <p>경로는 <b>permitAll</b>이다 — {@code SecurityConfig}의 공개 티어와 {@code PublicPaths.ALL} <b>양쪽</b>에 등록돼
 * 있다(한쪽만 등록하면 만료된 access 토큰을 든 브라우저가 가입·로그인 화면에서 401 {@code TOKEN_EXPIRED}를 맞는다 — #181이 고친 버그).
 * 로그인은 특히 <b>로그아웃하고 다시 들어오는 화면</b>이라 만료 토큰이 남아 있기 쉬운 자리다.
 *
 * <p>스펙: docs/api/specs/01-auth-onboarding.md §1-3·§1-4.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class WebAuthController {

  private final WebAuthService webAuthService;
  private final RefreshTokenCookies refreshTokenCookies;

  /**
   * 회원가입. refresh 토큰은 <b>응답 본문에 담지 않고</b> {@code Set-Cookie}로만 내린다(ADR-0048) — 본문에도 실으면 {@code
   * HttpOnly}로 막으려던 XSS 유출 경로가 그대로 열린다.
   */
  @PostMapping("/signup")
  public SignupResponse signup(
      @Valid @RequestBody SignupRequest request, HttpServletResponse response) {
    SignupResult result = webAuthService.signup(request);
    response.addHeader(
        HttpHeaders.SET_COOKIE, refreshTokenCookies.build(result.refreshToken()).toString());
    return result.response();
  }

  /**
   * 로그인. 가입과 같은 쿠키 조립기({@link RefreshTokenCookies})를 쓴다 — 발급 경로마다 속성을 따로 적으면 {@code Path}나 {@code
   * SameSite} 한 글자가 갈리는 순간 브라우저가 <b>다른 쿠키로 보고</b> 재발급·로그아웃이 조용히 어긋난다.
   *
   * <p><b>호출자 IP를 여기서 뽑아 넘긴다</b>({@link ClientIps}) — 로그인은 시도 레이트리밋이 붙는 경로이고 그 축 하나가 IP다(US-1-12).
   * 가입용 SMS 인증({@link SignupPhoneVerificationController})과 같은 추출 규칙을 공유해야 같은 호출자가 두 경로에서 같은 버킷에
   * 들어간다. {@link jakarta.servlet.http.HttpServletRequest}는 이 계층에서 문자열로 바뀌어 응용 계층에는 넘어가지 않는다.
   *
   * <p>실패는 전부 예외로 나간다 — 시도 한도 초과 429({@code TOO_MANY_REQUESTS})·잘못된 자격증명 401({@code
   * AUTH_INVALID_CREDENTIALS})·잠긴 계정 423({@code AUTH_ACCOUNT_LOCKED}) 모두 {@code
   * GlobalExceptionHandler}가 공통 에러 스키마로 변환하므로 여기서는 분기하지 않는다. 즉 이 메서드가 반환까지 도달했다면 로그인은 이미 성공한 것이라
   * <b>쿠키를 조건 없이</b> 싣는다.
   */
  @PostMapping("/login")
  public LoginResponse login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse response) {
    LoginResult result = webAuthService.login(request, ClientIps.resolve(servletRequest));
    response.addHeader(
        HttpHeaders.SET_COOKIE, refreshTokenCookies.build(result.refreshToken()).toString());
    return result.response();
  }
}
