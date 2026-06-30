package com.kohere.auth.infrastructure;

import com.kohere.auth.application.AuthProperties;
import com.kohere.auth.domain.PhoneVerificationCodeHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 연락처 인증번호의 단방향 해시. 저장소에는 원문이 아닌 {@code SHA-256(code + pepper)}만 보관한다. 이메일 인증번호 해셔와 동형이되 별도
 * pepper({@code app.auth.phone-pepper})를 쓴다.
 */
@Component
public class PhoneVerificationCodeHasherImpl implements PhoneVerificationCodeHasher {

  private final String pepper;

  public PhoneVerificationCodeHasherImpl(AuthProperties authProperties) {
    this.pepper = authProperties.getPhonePepper();
  }

  @Override
  public String hash(String code) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest((code + pepper).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm unavailable", e);
    }
  }
}
