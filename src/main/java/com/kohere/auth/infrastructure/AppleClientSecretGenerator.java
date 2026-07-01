package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.AppleUpstreamException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Apple {@code client_secret}(ES256 JWT) 서명·캐시(ADR-0031 #6). client_secret은 사용자와 무관한 <b>앱 단위 단일
 * 값</b>이라 인스턴스별로 인메모리에 1개만 캐시하고, 만료가 임박하면 같은 {@code .p8}로 재서명해 교체한다. {@code /auth/token}과 {@code
 * /auth/revoke}가 공유한다.
 *
 * <p>{@code .p8} 개인키와 서명된 JWT는 절대 로깅하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AppleClientSecretGenerator {

  /** 만료 직전 재서명 여유(초) — 만료 경계의 시계 오차로 거절되는 것을 피한다. */
  private static final long REFRESH_SKEW_SECONDS = 60;

  private final AppleProperties properties;
  private final Object lock = new Object();

  private volatile String cachedSecret;
  private volatile Instant expiresAt;
  private volatile ECPrivateKey cachedPrivateKey;

  /** 캐시된 client_secret을 반환하되, 비었거나 만료가 임박했으면 재서명해 교체한다. */
  public String generate() {
    String secret = cachedSecret;
    Instant exp = expiresAt;
    if (secret != null
        && exp != null
        && Instant.now().isBefore(exp.minusSeconds(REFRESH_SKEW_SECONDS))) {
      return secret;
    }
    synchronized (lock) {
      if (cachedSecret != null
          && expiresAt != null
          && Instant.now().isBefore(expiresAt.minusSeconds(REFRESH_SKEW_SECONDS))) {
        return cachedSecret;
      }
      Instant issuedAt = Instant.now();
      Instant newExpiry = issuedAt.plusSeconds(properties.getClientSecretTtlSeconds());
      cachedSecret = sign(issuedAt, newExpiry);
      expiresAt = newExpiry;
      return cachedSecret;
    }
  }

  private String sign(Instant issuedAt, Instant expiry) {
    try {
      JWSSigner signer = new ECDSASigner(privateKey());
      JWSHeader header =
          new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(properties.getKeyId()).build();
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .issuer(properties.getTeamId())
              .subject(properties.getClientId())
              .audience(properties.getAudience())
              .issueTime(Date.from(issuedAt))
              .expirationTime(Date.from(expiry))
              .build();
      SignedJWT jwt = new SignedJWT(header, claims);
      jwt.sign(signer);
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new AppleUpstreamException(e);
    }
  }

  private ECPrivateKey privateKey() {
    ECPrivateKey key = cachedPrivateKey;
    if (key != null) {
      return key;
    }
    synchronized (lock) {
      if (cachedPrivateKey == null) {
        cachedPrivateKey = parsePrivateKey(properties.getPrivateKey());
      }
      return cachedPrivateKey;
    }
  }

  /** .p8(PKCS#8 PEM)을 EC 개인키로 파싱한다. env 주입 시 개행이 {@code \\n}로 이스케이프된 경우도 허용한다. */
  private static ECPrivateKey parsePrivateKey(String pem) {
    if (pem == null || pem.isBlank()) {
      throw new AppleUpstreamException(
          new IllegalStateException("Apple private key is not configured"));
    }
    try {
      String normalized =
          pem.replace("\\n", "\n")
              .replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s", "");
      byte[] der = Base64.getDecoder().decode(normalized);
      KeyFactory keyFactory = KeyFactory.getInstance("EC");
      return (ECPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new AppleUpstreamException(e);
    }
  }
}
