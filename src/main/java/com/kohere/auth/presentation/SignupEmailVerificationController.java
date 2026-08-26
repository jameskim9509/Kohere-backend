package com.kohere.auth.presentation;

import com.kohere.auth.application.SignupEmailVerificationService;
import com.kohere.auth.application.dto.SignupEmailVerificationCodeResponse;
import com.kohere.auth.application.dto.SignupEmailVerifyResponse;
import com.kohere.auth.presentation.dto.SignupEmailVerificationCodeRequest;
import com.kohere.auth.presentation.dto.SignupEmailVerifyRequest;
import com.kohere.common.request.ClientIps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입용 이메일 인증 REST 컨트롤러(임대인 웹·비로그인, US-1-18). 입력 검증·DTO 변환과 <b>호출자 IP 추출</b>만 담당하고 비즈니스 로직은 응용 계층에
 * 위임한다(docs/convention/code-style.md §3-3). 공통 래퍼는 {@link
 * com.kohere.common.response.ApiResponseWrapper}가 자동 적용한다(ADR-0013).
 *
 * <p><b>왜 {@link AuthController}에 넣지 않는가</b> — {@link SignupPhoneVerificationController}와 같은 판단이다.
 * {@code AuthController}의 엔드포인트는 전부 (1) 단일 협력자 {@code AuthService}에 위임하고 (2) 소셜 로그인으로 시작하는 하나의 계정
 * 생애주기에 속하며 (3) 대부분 {@code @AuthenticationPrincipal}로 주체를 받는다. 이 두 엔드포인트는 셋 다 어긋난다 — 협력자가 다르고(가입용
 * 챌린지 전용 서비스), 계정이 아직 없는 웹 가입 트랙이며, 주체 대신 <b>{@link HttpServletRequest}에서 IP를 꺼내야</b> 한다.
 *
 * <p><b>경로가 한 세그먼트 깊다</b>({@code /auth/email/signup/*}) — 정식 사용자용 {@code
 * /auth/email/verification-code}·{@code /auth/email/verify}는 {@code SecurityConfig}에서 {@code
 * hasRole("USER")} <b>정확 경로 매처</b>라 이 경로를 덮지 않는다. 다만 <b>명시하지 않으면 {@code
 * anyRequest().authenticated()}로 떨어져 401</b>이므로, 공개 티어에 반드시 먼저 선언하고 {@code PublicPaths.ALL}에도
 * <b>함께</b> 등록해야 한다(한쪽만 등록하면 만료된 access 토큰을 든 브라우저가 가입 화면에서 401 {@code TOKEN_EXPIRED}를 맞는다 — #181이
 * 고친 버그).
 *
 * <p><b>조건부 등록이 없다.</b> 이메일 인증 절차는 모든 실행 환경에서 같은 흐름으로 돌며, 설정으로 갈리는 것은 발송 채널({@code
 * app.mail.enabled})뿐이다. 기능 토글을 달면 꺼진 환경에서 마커를 만들 방법이 없어 가입이 막히거나(422 반복), 게이트까지 함께 꺼서 <b>인증 없이
 * 가입되는</b> 둘 중 하나가 된다.
 *
 * <p>스펙: docs/api/specs/01-auth-onboarding.md §1-11·§1-12.
 */
@RestController
@RequestMapping("/api/v1/auth/email/signup")
@RequiredArgsConstructor
public class SignupEmailVerificationController {

  private final SignupEmailVerificationService signupEmailVerificationService;

  /**
   * 호출자 IP는 {@link ClientIps}가 뽑는다 — 가입용 SMS 인증·웹 로그인도 같은 값을 레이트리밋 키로 쓰는데, 추출 규칙이 여러 벌이면 같은 호출자가
   * 엔드포인트마다 다른 버킷에 들어가 한도가 의도대로 걸리지 않는다. 서블릿 타입은 여기까지만 오고 응용 계층에는 문자열만 내려간다.
   */
  @PostMapping("/verification-code")
  public SignupEmailVerificationCodeResponse sendCode(
      @Valid @RequestBody SignupEmailVerificationCodeRequest request,
      HttpServletRequest servletRequest) {
    return signupEmailVerificationService.sendCode(
        request.email(), ClientIps.resolve(servletRequest));
  }

  @PostMapping("/verify")
  public SignupEmailVerifyResponse verify(@Valid @RequestBody SignupEmailVerifyRequest request) {
    return signupEmailVerificationService.verify(request.email(), request.code());
  }
}
