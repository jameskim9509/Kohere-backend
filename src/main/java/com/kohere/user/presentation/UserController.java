package com.kohere.user.presentation;

import com.kohere.common.response.ApiResponse;
import com.kohere.user.application.UserService;
import com.kohere.user.application.dto.UserProfileResponse;
import com.kohere.user.presentation.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 프로필·계정 REST 컨트롤러. 입력 검증·DTO 변환만 담당하고 비즈니스 로직은 응용 계층에 위임한다 (docs/convention/code-style.md §3-3).
 * 응답은 공통 래퍼로 감싼다.
 *
 * <p>스펙: docs/api/specs/01-auth-onboarding.md §5~7 ({@code /api/v1/users/me}). 인증 주체(userId)는
 * SecurityContext에서 처리한다(TODO: 보안 설정 후 연동).
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping
  public ApiResponse<UserProfileResponse> getMyProfile() {
    return ApiResponse.success(userService.getMyProfile());
  }

  @PatchMapping
  public ApiResponse<UserProfileResponse> updateMyProfile(
      @Valid @RequestBody UpdateProfileRequest request) {
    return ApiResponse.success(userService.updateMyProfile(request));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void withdraw() {
    userService.withdraw();
  }
}
