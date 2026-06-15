package com.kohere.chat.domain;

import java.util.Optional;

/**
 * 메시지 영속 포트. 구현은 infrastructure 계층에 둔다(docs/convention/code-style.md §3-3).
 *
 * <p>TODO: 방별 커서 페이지네이션 조회(최신순), 안읽음 수 집계 메서드를 추가한다.
 */
public interface MessageRepository {

  Optional<Message> findById(Long messageId);
}
