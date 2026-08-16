package com.kohere.user.domain;

import java.util.Optional;

/**
 * 회원 영속 포트. 구현은 infrastructure 계층에 두어 의존성을 역전한다(docs/convention/code-style.md §3-3). 도메인은 영속 기술을
 * 모른다.
 */
public interface UserRepository {

  Optional<User> findById(Long id);

  /**
   * 연락처로 ACTIVE 임대인의 식별자를 조회하면서 <b>그 행에 쓰기 잠금을 건다</b>(SELECT … FOR UPDATE). 잠금은 호출자 트랜잭션이 끝날 때까지
   * 유지된다.
   *
   * <p>메서드 이름에 {@code ForUpdate}를 남기는 것은 의도다 — 잠금은 호출부의 정합성 전제이지 최적화가 아니라서, 이름에서 사라지면 "그냥 조회"로 오해되고
   * 다른 조회로 갈아 끼워진다. 반환이 식별자뿐인 것도 같은 이유다: 호출부(웹 가입 연동 판정)가 필요한 것은 <b>존재 여부와 id</b>뿐이고, 프로필을 돌려주면 그
   * 값을 폼 값으로 덮어쓰는 코드가 붙기 쉽다(연동 시 {@code users}는 한 칼럼도 바꾸지 않는다 — ADR-0047 §6).
   *
   * @param phoneNumber 숫자만 남긴 표준형 번호
   */
  Optional<Long> findActiveLandlordIdByPhoneNumberForUpdate(String phoneNumber);

  /** 닉네임 전역 유니크 충돌 검사용(NicknameGenerator). */
  boolean existsByNickname(String nickname);

  /** 신규 저장·변경 저장(upsert). 신규는 식별자가 채워진 인스턴스를 반환한다. */
  User save(User user);
}
