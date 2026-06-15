package com.kohere.community.domain;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 게시글 애그리거트 루트. 영속 기술(JPA)에 의존하지 않는 순수 도메인 모델이다. 영속 매핑은 infrastructure 계층의 어댑터에서
 * 처리한다(docs/convention/code-style.md §3-3).
 *
 * <p>게시글은 텍스트(제목+본문)만 지원한다. 삭제는 소프트 삭제({@code deleted})로 처리하며 목록·상세에서 제외한다. 스펙:
 * docs/api/specs/05-community.md.
 *
 * <p>TODO: 도메인 불변식·상태 전이(작성자만 수정/삭제, 좋아요/댓글/공유 카운트 증감, 음수 방지) 메서드를 채운다.
 */
@Getter
@Builder
public class Post {

  private final Long id;
  private final Long authorId;
  private final BoardType boardType;
  private final String title;
  private final String content;
  private final List<String> hashtags;
  private final int likeCount;
  private final int commentCount;
  private final int shareCount;
  private final boolean deleted;
  private final Instant createdAt;
  private final Instant updatedAt;
}
