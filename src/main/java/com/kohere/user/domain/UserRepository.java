package com.kohere.user.domain;

import java.util.Optional;

/**
 * 회원 영속 포트. 구현은 infrastructure 계층에 두어 의존성을 역전한다(docs/convention/code-style.md §3-3). 도메인은 영속 기술을
 * 모른다.
 *
 * <p>TODO: 프로필 수정 저장, 탈퇴(상태 전이) 영속 메서드를 추가한다.
 */
public interface UserRepository {

  Optional<User> findById(Long id);
}
