package com.kohere.common.exception;

import java.util.Arrays;

/**
 * 입력값이 의미상 올바르지 않을 때(예: enum 문자열 매핑 실패). 전역 핸들러가 400 {@code INVALID_INPUT}으로 변환한다.
 *
 * <p>Bean Validation으로 잡히는 형식 위반은 핸들러가 {@code INVALID_INPUT}으로 직접 매핑하므로, 이 예외는 응용/도메인 계층에서 형식은
 * 통과했으나 값이 유효하지 않을 때 쓴다.
 *
 * <p><b>어느 필드가 문제인지 아는 자리에서는 {@link #InvalidInputException(String, String, Object...)}을 쓴다.</b> 응답
 * {@code error.message}는 리소스 번들의 일반 문구로 덮이므로, 필드 단위 사유를 전달할 수 있는 유일한 통로가 {@code error.errors[]}이고 이
 * 생성자가 그것을 채운다 — Bean Validation 위반과 같은 모양의 응답이 된다(#151).
 *
 * <p><b>사유는 문장이 아니라 번들 키로 넘긴다.</b> {@code messages[_&lt;lang&gt;].properties}의 {@code validation.*}
 * 키와 인자를 받아 핸들러가 요청 locale로 해소한다(ADR-0030) — 자바 문자열을 그대로 넘기면 {@code message}는 번역되는데 {@code reason}만
 * 한 언어에 고정되는 불일치가 생긴다. 키가 없으면 넘긴 문자열이 그대로 노출되므로, 전환하지 않은 자리도 종전과 같이 동작한다.
 *
 * <p>필드명은 <b>클라이언트가 보낸 요청 필드·쿼리 파라미터·경로 변수 이름</b>이어야 한다. 도메인 내부 이름이나 저장소 문서의 경로를 넣으면 클라이언트가 고칠 수 없는
 * 값을 가리키게 되므로, 그런 자리에서는 message 만 받는 생성자를 쓴다.
 */
public class InvalidInputException extends BusinessException {

  private final String field;
  private final String reasonCode;
  private final transient Object[] reasonArgs;

  public InvalidInputException() {
    super(ErrorCode.INVALID_INPUT);
    this.field = null;
    this.reasonCode = null;
    this.reasonArgs = new Object[0];
  }

  public InvalidInputException(String message) {
    super(ErrorCode.INVALID_INPUT, message);
    this.field = null;
    this.reasonCode = null;
    this.reasonArgs = new Object[0];
  }

  /**
   * @param field 클라이언트가 보낸 요청 필드·쿼리 파라미터·경로 변수 이름(중첩이면 {@code moveIn.from}처럼 경로)
   * @param reasonCode 리소스 번들의 {@code validation.*} 키 — 요청 locale로 해소돼 {@code
   *     error.errors[].reason}이 된다
   * @param reasonArgs 키 문구의 {@code {0}}·{@code {1}} 자리에 채울 값(허용 범위·받은 값 등)
   */
  public InvalidInputException(String field, String reasonCode, Object... reasonArgs) {
    super(ErrorCode.INVALID_INPUT, describe(field, reasonCode, reasonArgs));
    this.field = field;
    this.reasonCode = reasonCode;
    this.reasonArgs = reasonArgs == null ? new Object[0] : reasonArgs.clone();
  }

  /** 예외 메시지 — 응답에는 쓰이지 않고(번들이 덮는다) 로그·스택트레이스에만 남는다. 사유 키만으로는 어떤 값이 거절됐는지 알 수 없으므로 인자를 함께 적는다. */
  private static String describe(String field, String reasonCode, Object... reasonArgs) {
    return field
        + ": "
        + reasonCode
        + (reasonArgs == null || reasonArgs.length == 0 ? "" : " " + Arrays.toString(reasonArgs));
  }

  /** 필드를 특정했으면 그 이름, 아니면 {@code null}. */
  public String getField() {
    return field;
  }

  /** 사유 번들 키. 필드를 특정하지 않았으면 {@code null}. */
  public String getReasonCode() {
    return reasonCode;
  }

  public Object[] getReasonArgs() {
    return reasonArgs.clone();
  }
}
