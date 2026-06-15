package com.kohere.common.response;

import java.util.List;

/**
 * 에러 응답 상세. {@code code}로 클라이언트가 분기하고 {@code errors}에 입력 검증 실패 필드를 담는다.
 *
 * <p>docs/api/error-response-guide.md §1.
 */
public record ErrorResponse(String code, String message, List<FieldErrorDetail> errors) {}
