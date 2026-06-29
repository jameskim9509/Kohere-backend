package com.kohere.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kohere.auth.application.AuthProperties;
import org.junit.jupiter.api.Test;

/**
 * {@link TokenHasher} 단위 테스트 — refresh 토큰을 {@code SHA-256(token + pepper)}로 단방향 해시한다(ADR-0006). 외부
 * 의존이 없는 순수 변환이라 단위로 검증한다: 알려진 SHA-256 벡터로 알고리즘·hex 인코딩을 고정하고(구현 재현이 아닌 외부 기준), pepper가 토큰 뒤에
 * 결합되는지, 등치 조회를 위해 결정적인지, pepper가 결과에 기여하는지를 본다.
 */
class TokenHasherTest {

  /** FIPS 180-2 B.1 표준 벡터: {@code SHA-256("abc")}. */
  private static final String SHA256_ABC =
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

  @Test
  void hash_matchesKnownSha256Vector_whenPepperEmpty() {
    // pepper가 비면 SHA-256(token)과 같아야 한다 — 구현이 실제 SHA-256·소문자 hex임을 외부 기준으로 고정
    assertThat(hasher("").hash("abc")).isEqualTo(SHA256_ABC);
  }

  @Test
  void hash_appendsPepperAfterToken() {
    // token "ab" + pepper "c" = "abc" → 같은 표준 벡터. pepper가 토큰 '뒤'에 결합됨을 증명한다
    assertThat(hasher("c").hash("ab")).isEqualTo(SHA256_ABC);
  }

  @Test
  void hash_isDeterministic() {
    TokenHasher hasher = hasher("pepper");

    // 저장된 해시로 등치 조회를 하므로 같은 입력은 항상 같은 출력이어야 한다(BCrypt 등 salt 해시를 안 쓰는 이유)
    assertThat(hasher.hash("raw-token")).isEqualTo(hasher.hash("raw-token"));
  }

  @Test
  void hash_differsWhenPepperDiffers() {
    // 같은 토큰이라도 pepper가 다르면 결과가 달라야 한다 — pepper가 실제로 결합돼 보안에 기여
    assertThat(hasher("pepper-a").hash("raw-token"))
        .isNotEqualTo(hasher("pepper-b").hash("raw-token"));
  }

  private static TokenHasher hasher(String pepper) {
    AuthProperties properties = new AuthProperties();
    properties.setRefreshPepper(pepper);
    return new TokenHasher(properties);
  }
}
