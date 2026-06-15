package com.kohere.user.application;

import com.kohere.user.application.dto.UserProfileResponse;
import com.kohere.user.domain.UserRepository;
import com.kohere.user.presentation.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 회원 프로필·계정 lifecycle 유스케이스 조율. 도메인(포트)을 호출하고 흐름만 조율한다. 도메인 규칙은 엔티티/도메인 서비스에 둔다
 * (docs/convention/code-style.md §3-3).
 *
 * <p>의존성은 생성자 주입({@code @RequiredArgsConstructor})으로 받는다(§3-4). 인증 주체(userId)는 SecurityContext에서
 * 가져온다(TODO: 보안 설정 후 연동) — 따라서 메서드 시그니처에는 userId 파라미터를 두지 않는다.
 *
 * <p>TODO: 영속 계층(JPA) 도입 시 유스케이스에 트랜잭션 경계({@code @Transactional})를 추가한다.
 */
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  public UserProfileResponse getMyProfile() {
    throw new UnsupportedOperationException("TODO: 내 프로필 조회");
  }

  public UserProfileResponse updateMyProfile(UpdateProfileRequest request) {
    throw new UnsupportedOperationException("TODO: 내 프로필 부분 수정");
  }

  public void withdraw() {
    throw new UnsupportedOperationException("TODO: 회원 탈퇴(WITHDRAWN 전이, 토큰 일괄 무효화)");
  }
}
