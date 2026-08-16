package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.Provider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA 리포지토리(내부). 도메인 포트 {@code SocialAccountRepository}의 어댑터가 사용한다. */
interface SocialAccountJpaRepository extends JpaRepository<SocialAccountJpaEntity, Long> {

  Optional<SocialAccountJpaEntity> findByProviderAndProviderUserId(
      Provider provider, String providerUserId);

  /**
   * userId 매핑 <b>전부</b>. 파생 쿼리 이름이 {@code findAllBy…}인 것이 곧 계약이다 — {@code findByUserId}로 두면 Spring
   * Data가 단건 조회로 해석해 2행에서 {@code IncorrectResultSizeDataAccessException}을 던지는데, 병합(US-1-15) 이후 2행은
   * <b>정상 상태</b>라 그 예외는 곧 정상 데이터에서 나는 500이다.
   */
  List<SocialAccountJpaEntity> findAllByUserId(Long userId);

  /**
   * 병합(US-1-15)의 소셜 매핑 이전 — 한 UPDATE로 끝낸다. 엔티티를 읽어 {@code userId}를 바꿔 저장하는 방식을 쓰지 않는 이유는 그렇게 하면
   * <b>행이 몇 개인지 먼저 정해야 하기 때문</b>이다. 벌크 UPDATE는 0행·N행 모두 같은 코드로 안전하고, 영향 행 수를 {@code void}로 버려 호출부가
   * 단언할 수단 자체를 없앤다.
   *
   * <p>{@code flushAutomatically}·{@code clearAutomatically}를 켜지 않는다 — 이 트랜잭션에서 소셜 매핑 엔티티를 읽거나 고친
   * 적이 없어 플러시할 변경도, 무효화할 1차 캐시도 없다(병합 분기는 {@code users} 전이를 타지 않는다).
   */
  @Modifying
  @Query("update SocialAccountJpaEntity s set s.userId = :toUserId where s.userId = :fromUserId")
  void reassignUserId(@Param("fromUserId") long fromUserId, @Param("toUserId") long toUserId);

  void deleteByUserId(Long userId);
}
