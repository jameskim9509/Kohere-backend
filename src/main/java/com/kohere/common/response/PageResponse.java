package com.kohere.common.response;

import java.util.List;

/**
 * 오프셋 기반 페이지 응답. {@code content}와 페이지 메타({@link PageInfo})를 담는다.
 *
 * <p>docs/api/api-design-guide.md §4-1.
 */
public record PageResponse<T>(List<T> content, PageInfo page) {

  public static <T> PageResponse<T> of(List<T> content, PageInfo page) {
    return new PageResponse<>(content, page);
  }
}
