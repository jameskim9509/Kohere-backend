package com.kohere.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 웹 refresh 토큰 쿠키의 생성·읽기·삭제(ADR-0048). 임대인 웹은 refresh를 응답 본문이 아니라 {@code HttpOnly} 쿠키로 주고받으므로 그
 * <b>전송 채널</b>을 한 곳에 모은다 — 속성이 흩어지면 발급은 {@code Path=/api/v1/auth}인데 삭제는 {@code Path=/}가 되어 브라우저가 다른
 * 쿠키로 보고 지우지 못하는 식으로 조용히 어긋난다.
 *
 * <pre>
 * Set-Cookie: refreshToken=&lt;opaque&gt;; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=1209600
 * </pre>
 *
 * <p><b>{@code HttpOnly}는 설정이 아니라 고정</b>이다 — 이 쿠키의 존재 이유가 "스크립트가 읽을 수 없는 자리에 refresh를 둔다"이므로 끌 수
 * 있으면 안 된다. 나머지 넷({@code name}·{@code path}·{@code sameSite}·{@code secure})만 {@link
 * RefreshCookieProperties}로 뺀다.
 *
 * <p><b>{@code Max-Age}는 앱 refresh TTL({@code app.auth.refresh-ttl-seconds})을 그대로 쓴다</b> — 새 설정키를
 * 만들지 않는다(ADR-0048 §4). 쿠키 만료와 서버 저장소 TTL이 갈리면 어느 쪽이 진짜 만료인지가 두 벌이 된다.
 *
 * <p>이 저장소에 쿠키를 다루는 코드가 없었으므로 관용구를 여기서 세운다 — 서블릿 {@link Cookie}가 아니라 {@link ResponseCookie}를 쓴다.
 * {@code SameSite}는 서블릿 {@code Cookie} API에 아예 없어서 헤더를 손으로 조립해야 하는데, 그러면 인코딩·따옴표 처리까지 직접 떠안는다. 반대로
 * <b>읽기</b>는 요청 파싱이라 서블릿이 이미 해 둔 {@link HttpServletRequest#getCookies()}를 그대로 쓴다.
 *
 * <p>공유 커널에 두는 이유는 {@link RefreshCookieProperties} 참조. 쿠키를 실제로 내리는 것은 프레젠테이션 계층(컨트롤러)이며, 응용 계층은 토큰
 * 문자열만 돌려준다 — 서블릿 타입이 유스케이스로 새어 들어가지 않게 한다.
 */
@Component
public class RefreshTokenCookies {

  private final RefreshCookieProperties properties;
  private final Duration maxAge;

  public RefreshTokenCookies(
      RefreshCookieProperties properties,
      @Value("${app.auth.refresh-ttl-seconds}") long refreshTtlSeconds) {
    this.properties = properties;
    this.maxAge = Duration.ofSeconds(refreshTtlSeconds);
  }

  /**
   * 발급된 refresh 토큰을 담은 {@code Set-Cookie}를 만든다. 헤더로 내리는 것은 호출자(컨트롤러)의 몫이다 — {@code
   * response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())}.
   */
  public ResponseCookie build(String refreshToken) {
    return base(refreshToken).maxAge(maxAge).build();
  }

  /**
   * 브라우저가 저장된 쿠키를 버리게 하는 삭제 쿠키({@code Max-Age=0}). 값은 비운다.
   *
   * <p>브라우저는 <b>이름과 {@code Path}(·{@code Domain})가 모두 같아야</b> 같은 쿠키로 보고 지우므로, 삭제 쿠키도 발급과 동일한 속성으로
   * 조립한다({@link #base}를 공유하는 이유다). 로그아웃 응답에 함께 실린다.
   */
  public ResponseCookie delete() {
    return base("").maxAge(0).build();
  }

  /**
   * 요청 쿠키에서 refresh 토큰을 꺼낸다(재발급·로그아웃의 <b>쿠키 우선</b> 경로). 쿠키가 없거나 값이 비어 있으면 비어 있는 결과를 돌려주고, 호출자가 요청
   * 본문 fallback으로 넘어간다 — 여기서 예외를 던지면 앱(본문 방식) 요청이 전부 막힌다.
   */
  public Optional<String> read(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    for (Cookie cookie : cookies) {
      if (properties.getName().equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
        return Optional.of(cookie.getValue());
      }
    }
    return Optional.empty();
  }

  /** 발급·삭제가 공유하는 속성 조립. {@code HttpOnly}만 상수이고 나머지는 설정값이다. */
  private ResponseCookie.ResponseCookieBuilder base(String value) {
    return ResponseCookie.from(properties.getName(), value)
        .httpOnly(true)
        .secure(properties.isSecure())
        .sameSite(properties.getSameSite())
        .path(properties.getPath());
  }
}
