package com.kohere.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kohere.auth.application.AuthProperties;
import org.junit.jupiter.api.Test;

/**
 * {@link EmailVerificationCodeHasherImpl} 단위 테스트 — 이메일 인증번호를 {@code SHA-256(code + pepper)}로 해시한다.
 * TokenHasher와 동형이되 별도 pepper({@code app.auth.email-pepper})를 쓴다. 외부 의존 없는 순수 변환이라 단위로 검증한다: 알려진
 * SHA-256 벡터로 알고리즘을 고정하고, pepper 결합 순서·결정성, 그리고 refresh가 아닌 email pepper를 쓰는지를 본다.
 */
class EmailVerificationCodeHasherImplTest {

  /** FIPS 180-2 B.1 표준 벡터: {@code SHA-256("abc")}. */
  private static final String SHA256_ABC =
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

  @Test
  void hash_matchesKnownSha256Vector_whenPepperEmpty() {
    assertThat(hasher("").hash("abc")).isEqualTo(SHA256_ABC);
  }

  @Test
  void hash_appendsPepperAfterCode() {
    // code "ab" + pepper "c" = "abc" → 표준 벡터. pepper가 코드 뒤에 결합됨을 증명한다
    assertThat(hasher("c").hash("ab")).isEqualTo(SHA256_ABC);
  }

  @Test
  void hash_isDeterministic() {
    EmailVerificationCodeHasherImpl hasher = hasher("pepper");

    // 저장된 해시로 등치 비교하므로 같은 코드는 항상 같은 출력
    assertThat(hasher.hash("482915")).isEqualTo(hasher.hash("482915"));
  }

  @Test
  void hash_usesEmailPepperNotRefreshPepper() {
    // emailPepper만 비우고 refreshPepper를 채워도 결과는 SHA-256(code)과 같아야 한다 — refresh pepper는 섞이지 않는다
    AuthProperties properties = new AuthProperties();
    properties.setRefreshPepper("refresh-pepper");
    properties.setEmailPepper("");

    assertThat(new EmailVerificationCodeHasherImpl(properties).hash("abc")).isEqualTo(SHA256_ABC);
  }

  private static EmailVerificationCodeHasherImpl hasher(String pepper) {
    AuthProperties properties = new AuthProperties();
    properties.setEmailPepper(pepper);
    return new EmailVerificationCodeHasherImpl(properties);
  }
}
