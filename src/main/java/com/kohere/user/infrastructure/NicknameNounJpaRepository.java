package com.kohere.user.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA 리포지토리(내부). 활성 사물 풀 조회. */
interface NicknameNounJpaRepository extends JpaRepository<NicknameNounJpaEntity, Long> {

  List<NicknameNounJpaEntity> findByActiveTrue();
}
