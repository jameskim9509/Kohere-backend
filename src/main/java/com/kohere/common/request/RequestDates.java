package com.kohere.common.request;

import com.kohere.common.exception.InvalidInputException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 요청 본문의 날짜 문자열을 파싱한다.
 *
 * <p><b>왜 DTO에 {@code LocalDate}를 두지 않는가</b> — 요청 DTO가 {@code LocalDate} 필드를 선언하면 형식이 틀린 값이 역직렬화
 * 단계에서 거부돼 {@code MALFORMED_REQUEST}가 된다. 본문 JSON은 멀쩡한데 「본문을 해석할 수 없다」고 답하는 셈이고, 어느 필드가 문제인지도 응답에
 * 남지 않는다. 파싱을 응용 계층으로 내려 {@code INVALID_INPUT} + {@code errors[].field}로 돌려준다(#151).
 *
 * <p>필수 여부는 여기서 보지 않는다 — 요청 DTO의 {@code @NotBlank}가 담당하고, 그쪽도 {@code INVALID_INPUT}이라 응답 모양이 같다.
 */
public final class RequestDates {

  private RequestDates() {}

  /**
   * {@code YYYY-MM-DD} 문자열을 과거 날짜로 파싱한다.
   *
   * @param field 클라이언트가 보낸 요청 필드명 — {@code errors[].field}로 나간다
   * @param value 미전송({@code null})이면 {@code null}을 돌려준다(선택 필드의 미변경)
   * @throws InvalidInputException 형식이 {@code YYYY-MM-DD}가 아니거나 오늘 이후인 경우
   */
  public static LocalDate parsePast(String field, String value) {
    if (value == null) {
      return null;
    }
    LocalDate parsed;
    try {
      parsed = LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw new InvalidInputException(field, "validation.dateFormat", value);
    }
    if (!parsed.isBefore(LocalDate.now())) {
      throw new InvalidInputException(field, "validation.pastDate", value);
    }
    return parsed;
  }
}
