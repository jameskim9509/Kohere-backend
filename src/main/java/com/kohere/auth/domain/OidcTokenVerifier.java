package com.kohere.auth.domain;

/**
 * 소셜 OIDC idToken 검증 포트. 서명(JWKS)·iss·aud·exp를 검증하고 신원을 반환한다. 구현은 infrastructure(Nimbus
 * JwtDecoder). 검증 실패는 {@link InvalidSocialTokenException}(401).
 */
public interface OidcTokenVerifier {

  OidcUser verify(Provider provider, String idToken);
}
