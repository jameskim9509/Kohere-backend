package com.kohere.booking.domain;

/**
 * 예약 신고 사유(표시용, 특정 언어로 해소됨). {@code code}는 언어 무관 불변 식별자, {@code label}은 요청 언어의 표시 문자열(없으면 en 폴백)이다.
 * 코드 배포 없이 카탈로그 행 추가로 사유·언어를 늘릴 수 있다. docs/architecture/domain-model.md §5.
 */
public record ReportReason(String code, String label) {}
