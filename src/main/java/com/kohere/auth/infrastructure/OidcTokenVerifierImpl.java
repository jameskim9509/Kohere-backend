package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.InvalidSocialTokenException;
import com.kohere.auth.domain.OidcTokenVerifier;
import com.kohere.auth.domain.OidcUser;
import com.kohere.auth.domain.Provider;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 소셜 OIDC idToken 검증(Nimbus JwtDecoder + JWKS 캐시). 제공자별 디코더는 최초 사용 시 lazy 생성한다(기동 시 네트워크 호출 없음).
 * 서명(JWKS)·iss·aud·exp를 검증하고 실패 시 {@link InvalidSocialTokenException}(401)으로 변환한다(ADR-0009
 * §Context의 외부 발급자 비대칭 검증과 동일 구조).
 */
@Component
@RequiredArgsConstructor
public class OidcTokenVerifierImpl implements OidcTokenVerifier {

  private final OidcProperties oidcProperties;
  private final Map<Provider, JwtDecoder> decoders = new ConcurrentHashMap<>();

  @Override
  public OidcUser verify(Provider provider, String idToken) {
    try {
      Jwt jwt = decoderFor(provider).decode(idToken);
      String subject = jwt.getSubject();
      if (!StringUtils.hasText(subject)) {
        throw new InvalidSocialTokenException();
      }
      return new OidcUser(provider, subject, jwt.getClaimAsString("email"));
    } catch (JwtException e) {
      throw new InvalidSocialTokenException();
    }
  }

  private JwtDecoder decoderFor(Provider provider) {
    return decoders.computeIfAbsent(provider, this::buildDecoder);
  }

  private JwtDecoder buildDecoder(Provider provider) {
    OidcProperties.ProviderConfig config = oidcProperties.forProvider(provider);
    if (!StringUtils.hasText(config.getJwkSetUri()) || !StringUtils.hasText(config.getIssuer())) {
      throw new InvalidSocialTokenException();
    }
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(config.getJwkSetUri()).build();
    OAuth2TokenValidator<Jwt> validator =
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(config.getIssuer()),
            audienceValidator(config.getAudience()));
    decoder.setJwtValidator(validator);
    return decoder;
  }

  private static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
    return jwt -> {
      if (!StringUtils.hasText(audience) || jwt.getAudience().contains(audience)) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "Required audience is missing", null));
    };
  }
}
