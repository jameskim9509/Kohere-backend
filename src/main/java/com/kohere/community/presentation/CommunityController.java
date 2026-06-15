package com.kohere.community.presentation;

import com.kohere.common.response.ApiResponse;
import com.kohere.common.response.PageResponse;
import com.kohere.community.application.CommunityService;
import com.kohere.community.application.dto.CommentResponse;
import com.kohere.community.application.dto.LikeToggleResponse;
import com.kohere.community.application.dto.NeighborChatResponse;
import com.kohere.community.application.dto.PostDetailResponse;
import com.kohere.community.application.dto.PostSummaryResponse;
import com.kohere.community.application.dto.ShareResponse;
import com.kohere.community.domain.BoardType;
import com.kohere.community.presentation.dto.CreateCommentRequest;
import com.kohere.community.presentation.dto.CreatePostRequest;
import com.kohere.community.presentation.dto.UpdatePostRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 커뮤니티(게시판·동네친구) REST 컨트롤러. 입력 검증·DTO 변환만 담당하고 비즈니스 로직은 응용 계층에 위임한다 (docs/convention/code-style.md
 * §3-3). 응답은 공통 래퍼로 감싼다.
 *
 * <p>스펙: docs/api/specs/05-community.md. 인증 주체(userId)는 SecurityContext에서 가져온다(TODO: 보안 설정 후 연동) —
 * 서비스 시그니처에는 경로·바디 파라미터만 전달한다.
 */
@RestController
@RequestMapping("/api/v1/community/posts")
@RequiredArgsConstructor
public class CommunityController {

  private final CommunityService communityService;

  @GetMapping
  public ApiResponse<PageResponse<PostSummaryResponse>> list(
      @RequestParam(required = false) BoardType boardType,
      @RequestParam(defaultValue = "createdAt,desc") String sort,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String hashtag,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(
        communityService.list(boardType, sort, keyword, hashtag, page, size));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<PostDetailResponse> create(@Valid @RequestBody CreatePostRequest request) {
    return ApiResponse.success(communityService.create(request));
  }

  @GetMapping("/me")
  public ApiResponse<PageResponse<PostSummaryResponse>> myPosts(
      @RequestParam(required = false) BoardType boardType,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(communityService.myPosts(boardType, page, size));
  }

  @GetMapping("/{postId}")
  public ApiResponse<PostDetailResponse> detail(@PathVariable Long postId) {
    return ApiResponse.success(communityService.detail(postId));
  }

  @PatchMapping("/{postId}")
  public ApiResponse<PostDetailResponse> update(
      @PathVariable Long postId, @Valid @RequestBody UpdatePostRequest request) {
    return ApiResponse.success(communityService.update(postId, request));
  }

  @DeleteMapping("/{postId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long postId) {
    communityService.delete(postId);
  }

  @PostMapping("/{postId}/like")
  public ApiResponse<LikeToggleResponse> toggleLike(@PathVariable Long postId) {
    return ApiResponse.success(communityService.toggleLike(postId));
  }

  @PostMapping("/{postId}/share")
  public ApiResponse<ShareResponse> share(@PathVariable Long postId) {
    return ApiResponse.success(communityService.share(postId));
  }

  @GetMapping("/{postId}/comments")
  public ApiResponse<PageResponse<CommentResponse>> listComments(
      @PathVariable Long postId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(communityService.listComments(postId, page, size));
  }

  @PostMapping("/{postId}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<CommentResponse> addComment(
      @PathVariable Long postId, @Valid @RequestBody CreateCommentRequest request) {
    return ApiResponse.success(communityService.addComment(postId, request));
  }

  @DeleteMapping("/{postId}/comments/{commentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteComment(@PathVariable Long postId, @PathVariable Long commentId) {
    communityService.deleteComment(postId, commentId);
  }

  /**
   * 동네친구 1:1 채팅 시작. 신규 생성 시 201, 기존 방 반환 시 200이지만 스켈레톤에서는 201로 고정한다.
   *
   * <p>TODO: {@code created} 값에 따라 201/200을 분기하도록 응답 status를 동적으로 설정한다(스펙 05-community.md).
   */
  @PostMapping("/{postId}/chat")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<NeighborChatResponse> startNeighborChat(@PathVariable Long postId) {
    return ApiResponse.success(communityService.startNeighborChat(postId));
  }
}
