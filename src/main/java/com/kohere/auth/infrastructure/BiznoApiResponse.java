package com.kohere.auth.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 비즈노 fapi(`type=json`) 응답 매핑(ADR-0033). {@code resultCode=0}이 정상 서비스이고, {@code items}에 조회 결과 사업자
 * 목록이 담긴다. 검증에 쓰는 필드만 선언하고 나머지(company·cno·taxtype 등)는 무시한다. 사업자 상태는 {@code bstt}(텍스트)·{@code
 * bsttcd}(코드)·{@code EndDt}(폐업일)로 표현된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BiznoApiResponse(int resultCode, int totalCount, List<Item> items) {

  /** 사업자 1건 — {@code bno}는 하이픈 포함 사업자번호, {@code EndDt}는 폐업일(없으면 빈 문자열). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Item(String bno, String bstt, String bsttcd, @JsonProperty("EndDt") String endDt) {}
}
