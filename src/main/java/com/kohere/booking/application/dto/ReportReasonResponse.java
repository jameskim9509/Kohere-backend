package com.kohere.booking.application.dto;

import java.util.List;

/**
 * 예약 신고 사유 목록 응답. {@code code}는 언어 무관 불변 식별자, {@code label}은 서버가 사용자 표시 언어(user::api getLanguage)로
 * content 번들에서 번역한 표시 문자열이다. docs/api/specs/04-booking-inquiry-chat.md §7.
 */
public record ReportReasonResponse(List<Reason> reasons) {

  public record Reason(String code, String label) {}
}
