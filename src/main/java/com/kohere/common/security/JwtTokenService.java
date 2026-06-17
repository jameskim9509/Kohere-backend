package com.kohere.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * 서버 JWT(access/온보딩 임시 토큰) 발급·검증. 서명은 HS256 대칭키(ADR-0009), 시크릿·만료는 {@link JwtProperties}(ADR-0011).
 *
 * <p>검증 메커니즘은 공유 커널(common)에 두고, 발급은 auth가 이 서비스를 호출한다. 시크릿은 런타임 주입이라 서명 능력은 시크릿을 받은 경계 안에만
 * 있다(ADR-0009 D3). access 토큰은 {@code onboardingCompleted=true}, 신규 회원의 온보딩 임시 토큰은 {@code false}
 * 클레임을 담는다.
 */
@Service
public class JwtTokenService {

  static final String CLAIM_ONBOARDING_COMPLETED = "onboardingCompleted";

  private final SecretKey key;
  private final String issuer;
  private final long accessTtlSeconds;
  private final long onboardingTtlSeconds;

  public JwtTokenService(JwtProperties properties) {
    this.key =
        io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            properties.getSecret().getBytes(StandardCharsets.UTF_8));
    this.issuer = properties.getIssuer();
    this.accessTtlSeconds = properties.getAccessTtlSeconds();
    this.onboardingTtlSeconds = properties.getOnboardingTtlSeconds();
  }

  /** 정식 access 토큰(온보딩 완료). 만료 {@link #accessTtlSeconds()}. */
  public String issueAccessToken(long userId) {
    return issue(userId, true, accessTtlSeconds);
  }

  /** 신규 회원 온보딩 전용 임시 토큰(refresh 미발급). 만료 {@link #onboardingTtlSeconds()}. */
  public String issueOnboardingToken(long userId) {
    return issue(userId, false, onboardingTtlSeconds);
  }

  public long accessTtlSeconds() {
    return accessTtlSeconds;
  }

  public long onboardingTtlSeconds() {
    return onboardingTtlSeconds;
  }

  /**
   * 서명·만료를 검증하고 주체를 추출한다.
   *
   * @throws io.jsonwebtoken.ExpiredJwtException 만료된 토큰
   * @throws io.jsonwebtoken.JwtException 서명 위조 등 검증 실패
   */
  public AuthPrincipal parse(String token) {
    Jws<Claims> jws =
        Jwts.parser().verifyWith(key).requireIssuer(issuer).build().parseSignedClaims(token);
    Claims claims = jws.getPayload();
    long userId = Long.parseLong(claims.getSubject());
    boolean onboardingCompleted =
        Boolean.TRUE.equals(claims.get(CLAIM_ONBOARDING_COMPLETED, Boolean.class));
    return new AuthPrincipal(userId, onboardingCompleted);
  }

  private String issue(long userId, boolean onboardingCompleted, long ttlSeconds) {
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(issuer)
        .subject(String.valueOf(userId))
        .claim(CLAIM_ONBOARDING_COMPLETED, onboardingCompleted)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(ttlSeconds)))
        .signWith(key)
        .compact();
  }
}
