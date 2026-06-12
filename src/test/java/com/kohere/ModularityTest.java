package com.kohere;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * 모듈러 모놀리식 경계 검증 테스트.
 *
 * <p>모듈 간 순환 의존, internal 패키지 접근 위반을 빌드 시점에 잡는다. 규칙은 docs/convention/code-style.md §3 참고.
 */
class ModularityTest {

  @Test
  void verifyModularity() {
    ApplicationModules.of(KohereApplication.class).verify();
  }
}
