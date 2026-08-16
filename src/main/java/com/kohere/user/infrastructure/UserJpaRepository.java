package com.kohere.user.infrastructure;

import com.kohere.user.domain.UserStatus;
import com.kohere.user.domain.UserType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Spring Data JPA 리포지토리(내부). 도메인 포트 {@code UserRepository}의 어댑터가 이를 사용한다. */
interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

  boolean existsByNickname(String nickname);

  /**
   * 연락처 + 상태 + 역할로 회원 한 행을 <b>쓰기 잠금(SELECT … FOR UPDATE)</b>과 함께 조회한다 — 웹 가입의 계정 연동 판정(US-1-11)이
   * check-then-act라 판정과 삽입 사이에 다른 트랜잭션이 끼어들지 못하게 막는다.
   *
   * <p><b>이 저장소의 첫 비관적 잠금</b>이다. 스칼라 프로젝션({@code select u.id …})이 아니라 엔티티를 그대로 읽는 파생 쿼리를 쓴 것은,
   * {@code @Lock} + 파생 쿼리가 Spring Data에서 가장 검증된 조합이라 잠금 렌더링에 기대할 것이 확실하기 때문이다. 한 행짜리 조회라 읽는 컬럼이 늘어도
   * 비용 차이가 없다.
   *
   * <p><b>영속성 컨텍스트에 올라온 엔티티가 {@code UserRepositoryImpl.save}와 충돌하지 않는다</b> — 그쪽은 도메인 객체를 새 엔티티로 조립해
   * {@code save}(=id가 있으면 {@code merge})를 부르는데, {@code merge}는 같은 식별자의 관리 인스턴스가 이미 있으면 <b>그 인스턴스에
   * 상태를 복사</b>할 뿐 새로 붙이지 않는다. 게다가 행 잠금은 인스턴스가 아니라 <b>DB 트랜잭션</b>이 쥐고 있어 커밋·롤백 전까지 유지된다.
   *
   * <p>{@code uq_users_phone_number}(V23)가 있어 결과는 최대 한 행이다 — {@code Optional} 반환이 성립하는 근거다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<UserJpaEntity> findByPhoneNumberAndStatusAndUserType(
      String phoneNumber, UserStatus status, UserType userType);
}
