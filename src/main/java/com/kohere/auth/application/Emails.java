package com.kohere.auth.application;

import java.util.Locale;

/**
 * 이메일 정규화 규칙. {@link com.kohere.common.request.PhoneNumbers#normalize}가 번호에 대해 하는 일을 이메일에 대해 한다 —
 * 응용 계층 경계에서 <b>한 번만</b> 접고, 이후 Redis 키·레이트리밋 카운터 키·DB 조회가 전부 같은 표준형을 쓴다.
 *
 * <p><b>왜 필요한가</b> — 사용자는 같은 주소를 {@code Kim@Work.com}과 {@code kim@work.com}으로 번갈아 입력한다. 접지 않으면 발송은
 * 한 키로, 확인은 다른 키로 가서 <b>조용히 422</b>가 되고, 사용자에게는 "맞는 번호를 넣었는데 안 된다"로만 보인다(#229 D10이 번호에서 겪은 것과 같은
 * 함정).
 *
 * <p><b>DB 동작과도 일치한다</b> — {@code local_accounts}(V22)가 {@code DEFAULT CHARSET=utf8mb4}이고 COLLATE를
 * 지정하지 않아 MySQL 8 기본값 {@code utf8mb4_0900_ai_ci}(대소문자 무시)가 적용된다. 즉 {@code uq_local_accounts_email}과
 * {@code findByEmail}이 이미 대소문자를 구분하지 않으므로, 소문자로 접은 값으로 중복을 물어도 DB가 보는 것과 같은 답이 나온다.
 *
 * <p><b>로컬파트 대소문자를 접는 것은 표준상 손실이다</b>(RFC 5321은 로컬파트를 대소문자 구분으로 정의한다). 그럼에도 접는 이유는 실제 메일 제공자가 사실상
 * 전부 무시하고, 무엇보다 <b>DB 유일성이 이미 무시하고 있어서</b>다 — 여기서만 구분하면 인증은 통과했는데 가입에서 409가 나는 조합이 생긴다.
 *
 * <p>{@code auth} 안에서만 쓰므로 공유 커널({@code common.request})로 올리지 않는다({@link Masks}와 같은 판단) — 소비자가 생기면
 * 그때 옮긴다.
 */
final class Emails {

  private Emails() {}

  /**
   * 앞뒤 공백을 지우고 소문자로 접는다. <b>멱등</b>이며 {@code null}은 그대로 돌려준다 — 호출부가 이미 Bean
   * Validation({@code @NotBlank}·{@code @Email})을 지난 값을 넘기므로 정상 경로에서 {@code null}은 오지 않지만, 정규화가
   * NPE로 흐름을 끊는 쪽이 되어서는 안 된다.
   *
   * <p>{@link Locale#ROOT}로 접는 것은 의도다 — 기본 로케일이 터키어면 {@code I}가 점 없는 {@code ı}로 접혀(터키어 I 문제) 같은 주소가
   * 서버 로케일에 따라 다른 키가 된다.
   */
  static String normalize(String email) {
    if (email == null) {
      return null;
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
