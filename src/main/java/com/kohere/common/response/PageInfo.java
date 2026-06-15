package com.kohere.common.response;

/** 오프셋 기반 페이지 메타. docs/api/api-design-guide.md §4-1. */
public record PageInfo(int number, int size, long totalElements, int totalPages, boolean hasNext) {}
