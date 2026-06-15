package com.kohere.auth.application;

import com.kohere.auth.application.dto.SocialLoginResponse;
import com.kohere.auth.application.dto.TokenResponse;
import com.kohere.auth.domain.RefreshTokenRepository;
import com.kohere.auth.presentation.dto.LogoutRequest;
import com.kohere.auth.presentation.dto.OnboardingRequest;
import com.kohere.auth.presentation.dto.ReissueRequest;
import com.kohere.auth.presentation.dto.SocialLoginRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 인증·온보딩 유스케이스 조율. 도메인(포트)을 호출하고 흐름만 조율한다. 도메인 규칙은 엔티티/도메인 서비스에 둔다 (docs/convention/code-style.md
 * §3-3).
 *
 * <p>의존성은 생성자 주입({@code @RequiredArgsConstructor})으로 받는다(§3-4). 인증 주체(userId)는 SecurityContext에서
 * 가져온다(TODO: 보안 설정 후 연동). 온보딩·로그아웃 등 인증 필수 유스케이스는 메서드 시그니처에 userId를 받지 않고 SecurityContext로 해결한다.
 *
 * <p>TODO: 영속 계층(JPA) 도입 시 유스케이스에 트랜잭션 경계({@code @Transactional})를 추가한다.
 *
 * <p>TODO: 사용자 생성/상태 전이(PENDING→ACTIVE)는 user 모듈과 공개 API·이벤트로 협력한다(user 타입 직접 import 금지).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

  private final RefreshTokenRepository refreshTokenRepository;

  public SocialLoginResponse socialLogin(SocialLoginRequest request) {
    throw new UnsupportedOperationException("TODO: 소셜 idToken 검증·기존/신규 분기·서버 JWT 발급");
  }

  public TokenResponse onboarding(OnboardingRequest request) {
    throw new UnsupportedOperationException("TODO: 온보딩 제출·약관 검증·ACTIVE 전이·정식 토큰 발급");
  }

  public TokenResponse reissue(ReissueRequest request) {
    throw new UnsupportedOperationException("TODO: refresh 토큰 검증·회전·access 재발급");
  }

  public void logout(LogoutRequest request) {
    throw new UnsupportedOperationException("TODO: refresh 토큰 무효화(멱등)");
  }
}
