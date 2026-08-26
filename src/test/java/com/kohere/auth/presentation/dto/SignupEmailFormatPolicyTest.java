package com.kohere.auth.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 가입 경로 이메일 형식의 <b>경계</b>를 고정한다(US-1-11·US-1-18).
 *
 * <p><b>왜 따로 두는가</b> — 문서화 테스트의 400 예시는 {@code "not-an-email"}처럼 {@code @}조차 없는 값이라
 * <b>{@code @Email}만 붙어 있어도 그대로 통과</b>한다. 즉 규칙을 느슨하게 되돌려도 그 테스트는 초록이다. "최상위 도메인을 요구한다"는 이 경로만의 계약을
 * 실제로 지키는 것은 이 파일뿐이다.
 *
 * <p><b>세 DTO를 함께 본다</b> — 발송·확인·가입이 같은 규칙을 써야 한다. 한 곳만 느슨해지면 인증은 통과했는데 가입에서 400이 나거나(그 반대) 하는,
 * 사용자가 원인을 알 수 없는 조합이 생긴다.
 *
 * <p>검증 대상이 {@code @Pattern} 하나이므로 Spring 컨텍스트 없이 Bean Validation만 띄운다. 다른 필드는 이 테스트의 관심사가 아니라 전부
 * 유효한 값으로 채우고 {@code email} 위반만 센다.
 */
class SignupEmailFormatPolicyTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  /** 세 진입점이 같은 규칙을 쓰는지 한 번에 본다 — 각 DTO의 email 위반 개수를 돌려주는 함수들. */
  static Stream<Object[]> dtos() {
    return Stream.of(
        new Object[] {
          "발송(§1-11)",
          (Function<String, Set<String>>) SignupEmailFormatPolicyTest::codeRequestViolations
        },
        new Object[] {
          "확인(§1-12)",
          (Function<String, Set<String>>) SignupEmailFormatPolicyTest::verifyRequestViolations
        },
        new Object[] {
          "가입(§1-3)",
          (Function<String, Set<String>>) SignupEmailFormatPolicyTest::signupRequestViolations
        });
  }

  @DisplayName("정상 이메일은 세 진입점 모두 통과한다")
  @ParameterizedTest(name = "{0}")
  @MethodSource("dtos")
  void validEmail_passesEverywhere(String label, Function<String, Set<String>> violations) {
    for (String email :
        new String[] {
          "kim@work.com",
          "kim.lee+tag_1%x@sub.work.co.kr",
          "a@b.io", // 로컬파트 1자 · 최상위 도메인 2자 — 하한
          "UPPER@Work.COM" // 대소문자는 정규화가 접으므로 형식 위반이 아니다
        }) {
      assertThat(violations.apply(email)).as("%s / %s", label, email).isEmpty();
    }
  }

  @DisplayName("형식을 어긴 이메일은 세 진입점 모두 email 필드 위반이다")
  @ParameterizedTest(name = "{0}")
  @MethodSource("dtos")
  void invalidEmail_failsEverywhere(String label, Function<String, Set<String>> violations) {
    for (String email :
        new String[] {
          "kim@work", // 최상위 도메인 없음 — @Email 은 통과시키는 값이다(이 테스트의 핵심)
          "kim@work.c", // 최상위 도메인 1자
          "kim@@work.com",
          "kim work@work.com", // 공백
          "@work.com",
          "kim@",
          "kim(at)work.com",
          "한글@work.com" // ASCII 밖
        }) {
      assertThat(violations.apply(email)).as("%s / %s", label, email).contains("email");
    }
  }

  @Test
  @DisplayName("최상위 도메인이 없는 주소는 @Email 이라면 통과하므로, 이 규칙이 실제로 좁다는 것을 못박는다")
  void patternIsStricterThanBeanValidationEmail() {
    // @Email 이 허용하는 값 — 규칙을 @Email 로 되돌리면 이 단정이 깨진다.
    assertThat("kim@work".matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")).isFalse();
    assertThat(signupRequestViolations("kim@work")).contains("email");
  }

  private static Set<String> codeRequestViolations(String email) {
    return fields(validator.validate(new SignupEmailVerificationCodeRequest(email)));
  }

  private static Set<String> verifyRequestViolations(String email) {
    return fields(validator.validate(new SignupEmailVerifyRequest(email, "482913")));
  }

  private static Set<String> signupRequestViolations(String email) {
    return fields(
        validator.validate(
            new SignupRequest(
                "김임대", "1990-01-01", "01012345678", email, "Kohere1!", true, true, false)));
  }

  private static <T> Set<String> fields(Set<ConstraintViolation<T>> violations) {
    return violations.stream()
        .map(v -> v.getPropertyPath().toString())
        .collect(java.util.stream.Collectors.toSet());
  }
}
