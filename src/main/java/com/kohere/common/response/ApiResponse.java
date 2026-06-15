package com.kohere.common.response;

import java.util.List;

/**
 * 모든 API 응답을 감싸는 공통 래퍼. 성공·에러 모두 동일한 봉투(success/data/error)를 사용한다.
 *
 * <p>규약: docs/api/api-design-guide.md §3, docs/api/error-response-guide.md §1. 컨트롤러는 엔티티가 아닌 DTO를
 * 담아 반환한다.
 */
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null);
  }

  public static ApiResponse<Void> error(String code, String message) {
    return new ApiResponse<>(false, null, new ErrorResponse(code, message, List.of()));
  }

  public static ApiResponse<Void> error(
      String code, String message, List<FieldErrorDetail> errors) {
    return new ApiResponse<>(false, null, new ErrorResponse(code, message, errors));
  }
}
