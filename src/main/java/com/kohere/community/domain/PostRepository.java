package com.kohere.community.domain;

import java.util.Optional;

/**
 * 게시글 영속 포트. 구현은 infrastructure 계층에 두어 의존성을 역전한다(docs/convention/code-style.md §3-3). 도메인은 영속 기술을
 * 모른다.
 *
 * <p>TODO: 게시판별 목록·검색(keyword/hashtag)·정렬, 내 게시글 목록, 좋아요/댓글/공유 카운트 집계 쿼리 메서드를 추가한다.
 */
public interface PostRepository {

  Optional<Post> findById(Long postId);
}
