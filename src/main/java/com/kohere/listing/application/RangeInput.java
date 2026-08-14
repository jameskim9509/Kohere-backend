package com.kohere.listing.application;

import com.kohere.common.exception.InvalidInputException;

/**
 * 등록 폼 1칸으로 받은 {@code min~max} 문자열을 두 정수로 나눈다.
 *
 * <p>지점 운영층({@code 1~2})과 이용 연령대({@code 20~35})가 이 형식을 쓴다. 스키마는 두 필드로 나눠 저장하므로 파싱 결과만 도메인에 들어가고 원본
 * 문자열은 보관하지 않는다 — 두 정수에서 언제든 되돌릴 수 있어 중복이다.
 */
record RangeInput(int min, int max) {

  private static final String SEPARATOR = "~";

  /**
   * {@code min~max}를 파싱한다.
   *
   * @param field 실패 시 {@code errors[].field}에 실을 요청 필드명
   */
  static RangeInput parse(String field, String raw) {
    if (raw == null || raw.isBlank()) {
      throw new InvalidInputException(field, "validation.required");
    }
    String[] parts = raw.split(SEPARATOR, -1);
    if (parts.length != 2) {
      throw new InvalidInputException(field, "validation.rangeFormat", raw);
    }
    int min = parseInt(field, raw, parts[0]);
    int max = parseInt(field, raw, parts[1]);
    if (min > max) {
      throw new InvalidInputException(field, "validation.notGreaterThan", "max", min, max);
    }
    return new RangeInput(min, max);
  }

  private static int parseInt(String field, String raw, String token) {
    try {
      return Integer.parseInt(token.trim());
    } catch (NumberFormatException e) {
      throw new InvalidInputException(field, "validation.rangeFormat", raw);
    }
  }
}
