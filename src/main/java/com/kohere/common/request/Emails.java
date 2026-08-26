package com.kohere.common.request;

import java.util.Locale;

/**
 * 요청으로 들어온 이메일의 <b>허용 형식</b>과 저장·비교용 표준형을 정한다({@link PhoneNumbers}의 이메일판).
 *
 * <p><b>왜 정규화가 필요한가</b> — 같은 사람이 {@code Kim@Work.com}과 {@code kim@work.com}을 번갈아 보내면 표기만 다른 같은 주소가
 * 서로 다른 값으로 남는다. 그러면 인증번호를 보낸 키와 확인하는 키가 어긋나 <b>조용히 실패</b>하고, 사용자에게는 "맞는 번호를 넣었는데 안 된다"로만 보인다. 그래서
 * <b>입력 경로에서 한 번</b> 접어 넣고 이후 저장·비교·키 생성은 전부 이 형태로만 한다.
 *
 * <p>형식 위반의 거부는 여기서 하지 않는다 — 요청 DTO의 {@code @NotBlank} + {@code @Pattern}({@link #PATTERN})이 맡고,
 * 그쪽도 {@code INVALID_INPUT}이라 응답 모양이 같다({@link PhoneNumbers}·{@link RequestDates}와 같은 분업).
 */
public final class Emails {

  /**
   * 요청 DTO {@code @Pattern}용 이메일 형식.
   *
   * <p><b>Bean Validation의 {@code @Email}보다 좁다.</b> 그쪽은 RFC를 넓게 해석해 {@code a@b}(최상위 도메인 없음)나 {@code
   * user@[10.0.0.1]}(IP 리터럴)까지 통과시키는데, 그런 주소는 <b>인증 메일이 도착할 수 없는 값</b>이라 받아 두면 사용자가 인증 단계에서야 막힌다. 이
   * 패턴은 로컬파트에 흔한 문자만 허용하고 도메인에 <b>점과 2자 이상의 최상위 도메인</b>을 요구해, 오타를 입력 시점에 잡는다.
   *
   * <p>그래서 이 패턴이 붙은 필드에는 {@code @Email}을 함께 달지 않는다 — 같은 값에 위반이 둘 붙어 {@code errors[]}에 같은 필드가 두 번
   * 실린다(사용자는 무엇을 고쳐야 하는지 두 번 듣는다).
   *
   * <p>위반 시 {@code 400 INVALID_INPUT} + {@code errors[].field=email}이며, 사유 문구는 {@code
   * Pattern.<dto>.email} 키로 두 번들(영어·한국어)에서 해소한다(ADR-0030).
   */
  public static final String PATTERN = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

  private Emails() {}

  /**
   * 앞뒤 공백을 지우고 소문자로 접은 표준형으로 만든다(예 {@code Kim@Work.COM } → {@code kim@work.com}). 멱등이라 이미 접힌 값을 다시
   * 넣어도 결과가 같다 — 경로 여러 곳에서 겹쳐 불러도 안전하다.
   *
   * <p><b>DB 동작과도 일치한다</b> — {@code local_accounts}(V22)가 {@code DEFAULT CHARSET=utf8mb4}이고
   * COLLATE를 지정하지 않아 MySQL 8 기본값 {@code utf8mb4_0900_ai_ci}(대소문자 무시)가 적용된다. 즉 {@code
   * uq_local_accounts_email}과 {@code findByEmail}이 이미 대소문자를 구분하지 않으므로, 소문자로 접은 값으로 물어도 DB가 보는 것과 같은
   * 답이 나온다.
   *
   * <p><b>로컬파트 대소문자를 접는 것은 표준상 손실이다</b>(RFC 5321은 로컬파트를 대소문자 구분으로 정의한다). 그럼에도 접는 이유는 실제 메일 제공자가 사실상
   * 전부 무시하고, 무엇보다 <b>DB 유일성이 이미 무시하고 있어서</b>다 — 여기서만 구분하면 인증은 통과했는데 가입에서 409가 나는 조합이 생긴다.
   *
   * <p>{@link Locale#ROOT}로 접는 것은 의도다 — 기본 로케일이 터키어면 {@code I}가 점 없는 {@code ı}로 접혀(터키어 I 문제) 같은 주소가
   * 서버 로케일에 따라 다른 키가 된다.
   *
   * @param email 미전송({@code null})이면 {@code null}을 돌려준다
   */
  public static String normalize(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }
}
