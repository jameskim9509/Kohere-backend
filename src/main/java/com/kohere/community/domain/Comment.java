package com.kohere.community.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * 댓글 엔티티. 게시글({@code postId})에 종속된다. 삭제는 소프트 삭제({@code deleted})로 처리한다.
 *
 * <p>스펙: docs/api/specs/05-community.md. TODO: 작성자만 삭제 가능 등 도메인 불변식 메서드를 채운다.
 */
@Getter
@Builder
public class Comment {

  private final Long id;
  private final Long postId;
  private final Long authorId;
  private final String content;
  private final boolean deleted;
  private final Instant createdAt;
}
