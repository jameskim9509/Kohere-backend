package com.kohere.common.response;

import java.util.List;

/**
 * 커서 기반 페이지 응답(채팅 메시지·무한 스크롤 피드). {@code nextCursor}를 클라이언트가 그대로 다시 보낸다.
 *
 * <p>docs/api/api-design-guide.md §4-2.
 */
public record CursorResponse<T>(List<T> content, String nextCursor, boolean hasNext) {}
