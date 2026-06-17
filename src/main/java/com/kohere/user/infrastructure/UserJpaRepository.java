package com.kohere.user.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA 리포지토리(내부). 도메인 포트 {@code UserRepository}의 어댑터가 이를 사용한다. */
interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {}
