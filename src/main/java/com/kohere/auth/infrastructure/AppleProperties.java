package com.kohere.auth.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Apple 인가코드 흐름 설정(app.apple, ADR-0031 #6). {@code clientId}는 네이티브 iOS App ID(번들 ID)로 {@code
 * app.oidc.apple.audience}와 동일해야 한다(Services ID 아님). {@code privateKey}(.p8)·식별자는 SSM에서 env로
 * 주입한다(ADR-0023). {@code privateKey}는 1급 시크릿이라 로그·{@code toString}에 노출하지 않는다 —
 * {@code @ConfigurationProperties}는 {@code toString}을 만들지 않으므로 별도 처리는 불필요하다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.apple")
public class AppleProperties {

  /** client_secret JWT의 {@code iss} = Apple Team ID. */
  private String teamId;

  /** client_secret JWT 헤더의 {@code kid} = Apple Key ID(.p8 발급 키). */
  private String keyId;

  /** client_secret JWT의 {@code sub} 및 {@code /auth/token} client_id = App ID(번들 ID). */
  private String clientId;

  /** ES256 서명용 .p8 개인키(PKCS#8 PEM). SSM SecureString → {@code APPLE_PRIVATE_KEY} env로 주입. */
  private String privateKey;

  /** authorization code 교환 엔드포인트. */
  private String tokenUrl = "https://appleid.apple.com/auth/token";

  /** refresh token 폐기 엔드포인트. */
  private String revokeUrl = "https://appleid.apple.com/auth/revoke";

  /** client_secret JWT의 {@code aud}(Apple 토큰 엔드포인트, 상수). */
  private String audience = "https://appleid.apple.com";

  /** client_secret 캐시 만료(초). Apple 상한은 6개월이나 짧게 두고 만료 전 재서명한다(ADR-0031 #6). */
  private long clientSecretTtlSeconds = 1800;

  /** Apple 외부 호출 connect 타임아웃(ms). 탈퇴·로그인을 막지 않도록 짧게 둔다(ADR-0031 #5). */
  private long connectTimeoutMillis = 3000;

  /** Apple 외부 호출 read 타임아웃(ms). */
  private long readTimeoutMillis = 5000;
}
