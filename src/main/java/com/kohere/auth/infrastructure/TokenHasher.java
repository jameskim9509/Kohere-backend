package com.kohere.auth.infrastructure;

import com.kohere.auth.application.AuthProperties;
import com.kohere.auth.domain.RefreshTokenHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 불투명 refresh 토큰의 단방향 해시. 저장소에는 원문이 아닌 {@code SHA-256(token + pepper)}만 보관한다(ADR-0006). 등치 조회가
 * 필요하므로 adaptive hash(BCrypt 등)는 쓰지 않는다.
 */
@Component
public class TokenHasher implements RefreshTokenHasher {

  private final String pepper;

  public TokenHasher(AuthProperties authProperties) {
    this.pepper = authProperties.getRefreshPepper();
  }

  @Override
  public String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest((token + pepper).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm unavailable", e);
    }
  }
}
