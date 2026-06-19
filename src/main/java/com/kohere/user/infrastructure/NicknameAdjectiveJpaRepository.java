package com.kohere.user.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA 리포지토리(내부). 활성 형용사 풀 조회. */
interface NicknameAdjectiveJpaRepository extends JpaRepository<NicknameAdjectiveJpaEntity, Long> {

  List<NicknameAdjectiveJpaEntity> findByActiveTrue();
}
