package com.kohere.common.response;

/** 입력 검증 실패 시 필드별 상세. docs/api/error-response-guide.md §1·§4. */
public record FieldErrorDetail(String field, String reason) {}
