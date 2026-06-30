package com.kohere.auth.infrastructure;

import com.kohere.auth.application.AuthProperties;
import com.kohere.auth.domain.BusinessRegistryNumberHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 사업자등록번호의 단방향 해시. 저장소·검증 마커 모두 원문이 아닌 {@code SHA-256(정규화번호 + pepper)}만 보관한다(원문·로그 비저장, 응답 마스킹). 별도
 * pepper({@code app.auth.business-pepper})를 쓴다(ADR-0033 · ADR-0015).
 */
@Component
public class BusinessRegistryNumberHasherImpl implements BusinessRegistryNumberHasher {

  private final String pepper;

  public BusinessRegistryNumberHasherImpl(AuthProperties authProperties) {
    this.pepper = authProperties.getBusinessPepper();
  }

  @Override
  public String hash(String businessRegistrationNumber) {
    String normalized = businessRegistrationNumber.replaceAll("\\D", "");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest((normalized + pepper).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm unavailable", e);
    }
  }
}
