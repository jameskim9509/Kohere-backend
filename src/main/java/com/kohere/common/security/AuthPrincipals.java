package com.kohere.common.security;

/**
 * {@link AuthPrincipal} 추출 헬퍼. 비회원(게스트)도 호출할 수 있는 {@code permitAll} 경로에서 인증 주체 부재를 안전하게 다루기 위한 공통
 * 정본이다(#181).
 *
 * <p>게스트는 합성 식별자를 발급받지 않고 <b>{@code userId == null}</b> 로 표현한다 — "신원 없음"을 뜻하는 의도적 값이며, 응용 계층은 이
 * null을 보고 사용자별 개인화(언어·소유권·적립)를 건너뛴다. 따라서 게스트가 닿을 수 있는 컨트롤러는 {@code principal.userId()}를 직접 역참조하면 안
 * 된다 — 토큰이 없으면 {@code @AuthenticationPrincipal}이 주입하는 주체 자체가 {@code null}이라 NPE가 난다.
 *
 * <p>회원 전용 경로(인가 단계에서 이미 인증을 요구한 경로)는 이 헬퍼가 필요 없다.
 */
public final class AuthPrincipals {

  private AuthPrincipals() {}

  /**
   * 인증 주체의 userId를 반환하되, 인증 정보가 없으면(비회원) {@code null}을 반환한다.
   *
   * @param principal 보안 필터가 주입한 인증 주체. 토큰 미전송·위조 시 {@code null}이다
   * @return 회원이면 userId, 게스트면 {@code null}
   */
  public static Long userIdOrNull(AuthPrincipal principal) {
    return principal == null ? null : principal.userId();
  }
}
