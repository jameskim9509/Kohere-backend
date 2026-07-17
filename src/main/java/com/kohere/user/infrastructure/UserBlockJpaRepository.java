package com.kohere.user.infrastructure;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data JPA 리포지토리(내부). 도메인 포트 {@code UserBlockRepository}의 어댑터가 사용한다. */
interface UserBlockJpaRepository extends JpaRepository<UserBlockJpaEntity, Long> {

  boolean existsByBlockerIdAndBlockedUserId(Long blockerId, Long blockedUserId);

  @Transactional
  void deleteByBlockerIdAndBlockedUserId(Long blockerId, Long blockedUserId);

  @Query("select b.blockedUserId from UserBlockJpaEntity b where b.blockerId = :blockerId")
  List<Long> findBlockedUserIds(@Param("blockerId") Long blockerId);

  @Query(
      "select count(b) > 0 from UserBlockJpaEntity b "
          + "where (b.blockerId = :a and b.blockedUserId = :b) "
          + "or (b.blockerId = :b and b.blockedUserId = :a)")
  boolean existsBetween(@Param("a") Long userA, @Param("b") Long userB);

  Page<UserBlockJpaEntity> findByBlockerId(Long blockerId, Pageable pageable);

  long countByBlockerId(Long blockerId);
}
