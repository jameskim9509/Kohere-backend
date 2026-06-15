package com.kohere.community.domain;

import lombok.Builder;
import lombok.Getter;

/**
 * 게시글 좋아요. {@code (postId, userId)} 유니크로 사용자당 1개를 보장한다(멱등 토글).
 *
 * <p>스펙: docs/api/specs/05-community.md 좋아요 토글.
 */
@Getter
@Builder
public class PostLike {

  private final Long id;
  private final Long postId;
  private final Long userId;
}
