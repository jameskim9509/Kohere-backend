package com.kohere.common.request;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 레이트리밋 키로 쓸 호출자 IP를 요청에서 꺼낸다. permitAll 경로는 토큰으로 주체를 묶을 수 없어 IP가 남는 몇 안 되는 식별자이고, 그 추출 규칙이 경로마다
 * 달라지면 같은 호출자가 엔드포인트마다 다른 버킷에 들어간다 — 그래서 규칙을 한 곳에 둔다.
 *
 * <p>앱은 dev·prod에서 Caddy 리버스 프록시 뒤에 있어({@code infra/terraform/modules/dev/host/Caddyfile.tftpl})
 * {@code getRemoteAddr()}가 프록시 주소로 고정되므로 {@code X-Forwarded-For}의 <b>최좌측(원 호출자) 항목</b>을 먼저 보고, 헤더가
 * 없을 때(로컬 직접 호출·테스트)만 remote address로 떨어진다.
 *
 * <p><b>이 값은 신뢰 경계가 아니다</b> — Caddy는 기존 {@code X-Forwarded-For}에 <b>덧붙이므로</b> 클라이언트가 헤더를 먼저 실어 보내면
 * 최좌측 값을 스스로 정할 수 있다. 그래서 IP 한도는 <b>비용을 늦추는 가드</b>일 뿐 인가 수단이 아니며, 우회할 수 없는 축을 함께 걸어야 방어가 성립한다(가입용
 * SMS는 대상 번호, 웹 로그인은 제출 이메일).
 *
 * <p><b>왜 {@code common}인가</b> — 서블릿 요청을 만지는 것은 프레젠테이션 계층뿐이지만 그 계층이 auth 안에만 있으리라는 보장이 없고, 이미
 * {@code common.security.RefreshTokenCookies}가 같은 이유로 {@link HttpServletRequest}를 다룬다. 응용 계층에는 언제나
 * 문자열만 내려간다.
 */
public final class ClientIps {

  private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

  private ClientIps() {}

  /**
   * 호출자 IP를 추출한다.
   *
   * @return 호출자 IP. 판별할 수 없으면 {@code null}이며, 이때 레이트리밋은 IP가 아닌 축만 적용한다
   */
  public static String resolve(HttpServletRequest request) {
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
