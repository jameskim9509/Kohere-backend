package com.kohere.community.application;

import com.kohere.common.response.PageResponse;
import com.kohere.community.application.dto.CommentResponse;
import com.kohere.community.application.dto.LikeToggleResponse;
import com.kohere.community.application.dto.NeighborChatResponse;
import com.kohere.community.application.dto.PostDetailResponse;
import com.kohere.community.application.dto.PostSummaryResponse;
import com.kohere.community.application.dto.ShareResponse;
import com.kohere.community.domain.BoardType;
import com.kohere.community.domain.CommentRepository;
import com.kohere.community.domain.PostRepository;
import com.kohere.community.presentation.dto.CreateCommentRequest;
import com.kohere.community.presentation.dto.CreatePostRequest;
import com.kohere.community.presentation.dto.UpdatePostRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 커뮤니티(게시판·동네친구) 유스케이스 조율. 도메인(포트)을 호출하고 흐름만 조율한다. 도메인 규칙은 엔티티/도메인 서비스에 둔다
 * (docs/convention/code-style.md §3-3).
 *
 * <p>의존성은 생성자 주입({@code @RequiredArgsConstructor})으로 받는다(§3-4). 인증 주체(userId)는 SecurityContext에서
 * 가져온다(TODO: 보안 설정 후 연동). 작성자 소유권(수정/삭제) 위반은 공통 {@code FORBIDDEN}으로 던진다.
 *
 * <p>TODO: 영속 계층(JPA) 도입 시 유스케이스에 트랜잭션 경계({@code @Transactional})를 추가한다.
 *
 * <p>TODO: 동네친구 채팅 시작({@link #startNeighborChat(Long)})은 chat 모듈의 {@code NEIGHBOR} 채팅방 생성 공개
 * API·이벤트로 위임한다(chat 모듈 타입 직접 import 금지).
 */
@Service
@RequiredArgsConstructor
public class CommunityService {

  private final PostRepository postRepository;
  private final CommentRepository commentRepository;

  public PageResponse<PostSummaryResponse> list(
      BoardType boardType, String sort, String keyword, String hashtag, int page, int size) {
    throw new UnsupportedOperationException("TODO: 게시글 목록·검색(게시판별, 정렬·keyword·hashtag, 페이지)");
  }

  public PostDetailResponse create(CreatePostRequest request) {
    throw new UnsupportedOperationException("TODO: 게시글 작성(작성자=인증 주체)");
  }

  public PageResponse<PostSummaryResponse> myPosts(BoardType boardType, int page, int size) {
    throw new UnsupportedOperationException("TODO: 내 게시글 목록(소프트 삭제 제외)");
  }

  public PostDetailResponse detail(Long postId) {
    throw new UnsupportedOperationException("TODO: 게시글 상세 조회(없거나 삭제 시 POST_NOT_FOUND)");
  }

  public PostDetailResponse update(Long postId, UpdatePostRequest request) {
    throw new UnsupportedOperationException("TODO: 게시글 부분 수정(작성자만, 아니면 FORBIDDEN)");
  }

  public void delete(Long postId) {
    throw new UnsupportedOperationException("TODO: 게시글 소프트 삭제(작성자만, 아니면 FORBIDDEN)");
  }

  public LikeToggleResponse toggleLike(Long postId) {
    throw new UnsupportedOperationException("TODO: 좋아요 토글(사용자당 1개, likeCount 정합)");
  }

  public ShareResponse share(Long postId) {
    throw new UnsupportedOperationException("TODO: 공유 카운트 증가(비멱등, 사용자 단위 레이트리밋)");
  }

  public PageResponse<CommentResponse> listComments(Long postId, int page, int size) {
    throw new UnsupportedOperationException("TODO: 댓글 목록 조회(작성순, 페이지)");
  }

  public CommentResponse addComment(Long postId, CreateCommentRequest request) {
    throw new UnsupportedOperationException("TODO: 댓글 작성(commentCount 1 증가)");
  }

  public void deleteComment(Long postId, Long commentId) {
    throw new UnsupportedOperationException("TODO: 댓글 소프트 삭제(작성자만, commentCount 1 감소·음수 방지)");
  }

  public NeighborChatResponse startNeighborChat(Long postId) {
    throw new UnsupportedOperationException(
        "TODO: 동네친구 1:1 채팅 시작(본인 글 불가, 멱등). chat 모듈 NEIGHBOR 채팅방 생성에 위임");
  }
}
