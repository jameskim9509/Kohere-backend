package com.kohere.user.domain;

import java.util.Optional;

/**
 * 회원 영속 포트. 구현은 infrastructure 계층에 두어 의존성을 역전한다(docs/convention/code-style.md §3-3). 도메인은 영속 기술을
 * 모른다.
 */
public interface UserRepository {

  Optional<User> findById(Long id);

  /** 닉네임 전역 유니크 충돌 검사용(NicknameGenerator). */
  boolean existsByNickname(String nickname);

  /** 신규 저장·변경 저장(upsert). 신규는 식별자가 채워진 인스턴스를 반환한다. */
  User save(User user);
}
