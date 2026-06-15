package com.kohere.community.domain;

import java.util.Optional;

/**
 * 댓글 영속 포트. 구현은 infrastructure 계층에 둔다(docs/convention/code-style.md §3-3).
 *
 * <p>TODO: 게시글별 댓글 목록(작성순) 조회 쿼리 메서드를 추가한다.
 */
public interface CommentRepository {

  Optional<Comment> findById(Long commentId);
}
