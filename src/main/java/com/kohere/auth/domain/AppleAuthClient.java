package com.kohere.auth.domain;

/**
 * Apple 인가코드 흐름 외부 연동 포트(ADR-0031). authorization code를 {@code id_token}+{@code refresh_token}으로
 * 교환하고, 탈퇴 시 {@code refresh_token}을 폐기한다. 구현(RestClient + ES256 {@code client_secret} 서명)은
 * infrastructure 어댑터가 담당한다(의존성 역전).
 *
 * <p>{@code client_secret}(ES256 JWT)은 앱 단위 단일 값이라 어댑터가 인메모리에 캐시하고 만료 전 재서명한다. 반환되는 {@code
 * refresh_token}·{@code client_secret}·{@code .p8}는 1급 시크릿이므로 로그·응답·{@code toString}에 노출하지 않는다.
 */
public interface AppleAuthClient {

  /**
   * authorization code를 Apple {@code POST /auth/token}에서 교환한다(grant_type=authorization_code, 네이티브
   * iOS이므로 redirect_uri 미전송). 교환 응답은 신뢰하지 않으며, 호출부가 반환된 {@code id_token}을 {@link
   * OidcTokenVerifier}로 재검증한다(ADR-0031 #1).
   *
   * @throws InvalidSocialTokenException 코드 만료·재사용·잘못된 client_secret 등 Apple 인증 실패(401로 변환)
   * @throws AppleUpstreamException Apple 측 일시 장애·타임아웃·5xx(502로 변환)
   */
  AppleTokens exchangeAuthorizationCode(String authorizationCode);

  /**
   * 저장된 Apple {@code refresh_token}을 {@code POST /auth/revoke}로
   * 폐기한다(token_type_hint=refresh_token). 멱등 — 이미 폐기된 토큰({@code invalid_grant}/{@code
   * invalid_token})은 성공으로 간주하고 반환한다.
   *
   * @throws AppleUpstreamException 그 외 실패(타임아웃·5xx 등). 탈퇴 흐름은 이를 best-effort로 처리한다(ADR-0031 #5).
   */
  void revokeRefreshToken(String refreshToken);

  /**
   * Apple {@code /auth/token} 교환 결과. {@code refreshToken}은 최초 동의/재동의 때만 채워지고 일반 재로그인에서는 null일 수 있다.
   * 절대 로그·응답에 노출하지 않는다.
   */
  record AppleTokens(String idToken, String refreshToken) {}
}
