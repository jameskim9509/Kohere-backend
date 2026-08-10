package com.kohere.common.exception;

import com.kohere.common.response.FieldErrorDetail;
import java.util.List;

/**
 * 입력값이 의미상 올바르지 않을 때(예: enum 문자열 매핑 실패). 전역 핸들러가 400 {@code INVALID_INPUT}으로 변환한다.
 *
 * <p>Bean Validation으로 잡히는 형식 위반은 핸들러가 {@code INVALID_INPUT}으로 직접 매핑하므로, 이 예외는 응용/도메인 계층에서 형식은
 * 통과했으나 값이 유효하지 않을 때 쓴다.
 *
 * <p><b>어느 필드가 문제인지 아는 자리에서는 {@link #InvalidInputException(String, String)}을 쓴다.</b> 응답 {@code
 * error.message}는 {@code messages.properties}의 일반 문구로 덮이므로, 생성자에 넘긴 message 는 클라이언트에게 닿지 않는다. 필드 단위
 * 사유를 전달할 수 있는 유일한 통로가 {@code error.errors[]}이고 이 생성자가 그것을 채운다 — Bean Validation 위반과 같은 모양의 응답이
 * 된다(#151).
 *
 * <p>필드명은 <b>클라이언트가 보낸 요청 필드·쿼리 파라미터 이름</b>이어야 한다. 도메인 내부 이름이나 저장소 문서의 경로를 넣으면 클라이언트가 고칠 수 없는 값을
 * 가리키게 되므로, 그런 자리에서는 message 만 받는 생성자를 쓴다.
 */
public class InvalidInputException extends BusinessException {

  private final transient List<FieldErrorDetail> fieldErrors;

  public InvalidInputException() {
    super(ErrorCode.INVALID_INPUT);
    this.fieldErrors = List.of();
  }

  public InvalidInputException(String message) {
    super(ErrorCode.INVALID_INPUT, message);
    this.fieldErrors = List.of();
  }

  /**
   * @param field 클라이언트가 보낸 요청 필드·쿼리 파라미터 이름(중첩이면 {@code moveIn.from}처럼 경로)
   * @param reason 그 필드가 왜 거절됐는지 — 응답 {@code error.errors[].reason}으로 그대로 나간다
   */
  public InvalidInputException(String field, String reason) {
    super(ErrorCode.INVALID_INPUT, field + ": " + reason);
    this.fieldErrors = List.of(new FieldErrorDetail(field, reason));
  }

  /** 응답 {@code error.errors[]}에 실릴 필드별 상세. 필드를 특정하지 않은 경우 빈 목록이다. */
  public List<FieldErrorDetail> getFieldErrors() {
    return fieldErrors;
  }
}
