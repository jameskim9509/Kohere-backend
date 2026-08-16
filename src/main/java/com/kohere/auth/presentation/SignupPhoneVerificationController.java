package com.kohere.auth.presentation;

import com.kohere.auth.application.SignupPhoneVerificationService;
import com.kohere.auth.application.dto.SignupPhoneVerificationCodeResponse;
import com.kohere.auth.application.dto.SignupPhoneVerifyResponse;
import com.kohere.auth.presentation.dto.SignupPhoneVerificationCodeRequest;
import com.kohere.auth.presentation.dto.SignupPhoneVerifyRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입용 연락처(SMS) 인증 REST 컨트롤러(임대인 웹·비로그인, US-1-13). 입력 검증·DTO 변환과 <b>호출자 IP 추출</b>만 담당하고 비즈니스 로직은 응용
 * 계층에 위임한다(docs/convention/code-style.md §3-3). 공통 래퍼는 {@link
 * com.kohere.common.response.ApiResponseWrapper}가 자동 적용한다(ADR-0013).
 *
 * <p><b>왜 {@link AuthController}에 넣지 않는가</b> — {@code AuthController}의 엔드포인트 11개는 전부 (1) 단일 협력자
 * {@code AuthService}에 위임하고 (2) 소셜 로그인으로 시작하는 하나의 계정 생애주기에 속하며 (3) 두 개(social-login·reissue)를 빼면 모두
 * {@code @AuthenticationPrincipal}로 주체를 받는다. 이 두 엔드포인트는 셋 다 어긋난다 — 협력자가 다르고(가입용 챌린지 전용 서비스), 계정이 아직
 * 없는 웹 가입 트랙이며, 주체 대신 <b>{@link HttpServletRequest}에서 IP를 꺼내야</b> 한다. 서블릿 요청을 다루는 메서드를 계정 생애주기 컨트롤러
 * 한가운데 끼워 넣으면 그 컨트롤러가 "입력 변환만 한다"는 성질이 깨지므로 채널 단위로 분리한다(경로 프리픽스가 {@code /auth/phone/signup}으로 이미
 * 갈라져 있어 URL 구조도 그대로다).
 *
 * <p>두 경로 모두 <b>permitAll</b>이다 — {@code SecurityConfig}의 공개 티어와 {@code PublicPaths.ALL} <b>양쪽</b>에
 * 등록돼 있다(한쪽만 등록하면 만료된 access 토큰을 든 브라우저가 가입 화면에서 401 {@code TOKEN_EXPIRED}를 맞는다 — #181이 고친 버그).
 *
 * <p>스펙: docs/api/specs/01-auth-onboarding.md §1-1·§1-2.
 */
@RestController
@RequestMapping("/api/v1/auth/phone/signup")
@RequiredArgsConstructor
public class SignupPhoneVerificationController {

  private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

  private final SignupPhoneVerificationService signupPhoneVerificationService;

  @PostMapping("/verification-code")
  public SignupPhoneVerificationCodeResponse sendCode(
      @Valid @RequestBody SignupPhoneVerificationCodeRequest request,
      HttpServletRequest servletRequest) {
    return signupPhoneVerificationService.sendCode(request.phoneNumber(), clientIp(servletRequest));
  }

  @PostMapping("/verify")
  public SignupPhoneVerifyResponse verify(@Valid @RequestBody SignupPhoneVerifyRequest request) {
    return signupPhoneVerificationService.verify(request.phoneNumber(), request.code());
  }

  /**
   * IP 레이트리밋 키로 쓸 호출자 IP. 앱은 dev·prod에서 Caddy 리버스 프록시 뒤에 있어({@code
   * infra/terraform/modules/dev/host/Caddyfile.tftpl}) {@code getRemoteAddr()}가 프록시 주소로 고정되므로
   * {@code X-Forwarded-For}의 <b>최좌측(원 호출자) 항목</b>을 먼저 보고, 헤더가 없을 때(로컬 직접 호출·테스트)만 remote address로
   * 떨어진다.
   *
   * <p><b>이 값은 신뢰 경계가 아니다</b> — Caddy는 기존 {@code X-Forwarded-For}에 <b>덧붙이므로</b> 클라이언트가 헤더를 먼저 실어
   * 보내면 최좌측 값을 스스로 정할 수 있다. 그래서 IP 한도는 <b>발송비·문자 폭탄을 늦추는 비용 가드</b>일 뿐 인가 수단이 아니며, 우회할 수 없는 방어는 대상
   * 번호로 거는 번호 한도와 재발송 쿨다운이다(#229 D6의 이중 한도가 필요한 이유). 프레젠테이션 계층에서만 서블릿 요청을 만지고 응용 계층에는 문자열로 내려보낸다.
   *
   * @return 호출자 IP. 판별할 수 없으면 {@code null}이며, 이때 레이트리밋은 번호 한도만 적용한다
   */
  private static String clientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
    if (StringUtils.hasText(forwardedFor)) {
      String leftmost = forwardedFor.split(",", 2)[0].trim();
      if (StringUtils.hasText(leftmost)) {
        return leftmost;
      }
    }
    return request.getRemoteAddr();
  }
}
