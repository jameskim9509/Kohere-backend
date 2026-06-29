package com.kohere.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * {@link AppleClientSecretGenerator} 단위 테스트 — ES256 서명, Apple이 요구하는 client_secret 클레임(iss/sub/aud +
 * header kid/alg, ADR-0031 #6), 인메모리 캐시 재사용, env 주입식 {@code \n}-이스케이프 PEM 파싱을 검증한다. 포트를 스텁하는 통합
 * 테스트가 닿지 못하는 실제 서명 경로다.
 */
class AppleClientSecretGeneratorTest {

  private static final KeyPair KEY_PAIR = generateP256();

  @Test
  void generate_signsEs256JwtWithAppleClaims() throws Exception {
    AppleClientSecretGenerator generator = new AppleClientSecretGenerator(properties(pem("\n")));

    SignedJWT jwt = SignedJWT.parse(generator.generate());

    assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
    assertThat(jwt.getHeader().getKeyID()).isEqualTo("KEY123");
    JWTClaimsSet claims = jwt.getJWTClaimsSet();
    assertThat(claims.getIssuer()).isEqualTo("TEAM123");
    assertThat(claims.getSubject()).isEqualTo("com.kohere.app");
    assertThat(claims.getAudience()).containsExactly("https://appleid.apple.com");
    assertThat(claims.getExpirationTime()).isAfter(new Date());
    // 서명이 발급 .p8의 공개키로 검증된다(= 올바른 키로 ES256 서명)
    assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) KEY_PAIR.getPublic()))).isTrue();
  }

  @Test
  void generate_returnsCachedSecretOnImmediateSecondCall() {
    AppleClientSecretGenerator generator = new AppleClientSecretGenerator(properties(pem("\n")));

    assertThat(generator.generate()).isEqualTo(generator.generate());
  }

  @Test
  void generate_acceptsEnvInjectedPemWithEscapedNewlines() {
    // SSM→.env 주입 시 .p8가 한 줄 + 리터럴 \n로 들어오는 경우를 허용해야 한다
    AppleClientSecretGenerator generator = new AppleClientSecretGenerator(properties(pem("\\n")));

    assertThatCode(generator::generate).doesNotThrowAnyException();
  }

  private static AppleProperties properties(String privateKeyPem) {
    AppleProperties properties = new AppleProperties();
    properties.setTeamId("TEAM123");
    properties.setKeyId("KEY123");
    properties.setClientId("com.kohere.app");
    properties.setAudience("https://appleid.apple.com");
    properties.setClientSecretTtlSeconds(1800);
    properties.setPrivateKey(privateKeyPem);
    return properties;
  }

  /** P-256 개인키를 PKCS#8 PEM으로 직렬화한다. {@code newline}으로 실제 개행/이스케이프(\n)를 골라 넣는다. */
  private static String pem(String newline) {
    String body = Base64.getEncoder().encodeToString(KEY_PAIR.getPrivate().getEncoded());
    return "-----BEGIN PRIVATE KEY-----" + newline + body + newline + "-----END PRIVATE KEY-----";
  }

  private static KeyPair generateP256() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(new ECGenParameterSpec("secp256r1"));
      return generator.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
