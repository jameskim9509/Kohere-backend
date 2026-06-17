package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.Provider;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 OIDC 제공자별 검증 설정(JWKS). issuer·jwkSetUri·audience(우리 client ID)를 담는다. MVP는 Google 우선(Apple 여유
 * 시).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.oidc")
public class OidcProperties {

  private ProviderConfig google = new ProviderConfig();
  private ProviderConfig apple = new ProviderConfig();

  public ProviderConfig forProvider(Provider provider) {
    return switch (provider) {
      case GOOGLE -> google;
      case APPLE -> apple;
    };
  }

  @Getter
  @Setter
  public static class ProviderConfig {
    private String issuer;
    private String jwkSetUri;
    private String audience;
  }
}
