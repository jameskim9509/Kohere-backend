package com.kohere.auth.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 웹 로컬 자격증명 JPA 엔티티(MySQL {@code local_accounts}). email 전역 유니크 — 웹 로그인 ID. user_id 유니크 — 한 계정에 웹
 * 자격증명 하나. userId는 user 소유라 FK 미설정(값 참조, ADR-0002/0005). 컬럼명은 기본 snake_case 매핑을 따르며 스키마는 Flyway
 * {@code V22__create_local_accounts.sql}이 소유한다. 도메인과 분리된 영속 모델로 어댑터가 매핑한다.
 *
 * <p>{@code passwordHash}는 BCrypt 해시로 1급 시크릿이다 — {@code @ToString} 미적용으로 로그 노출을 차단한다.
 */
@Entity
@Table(
    name = "local_accounts",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_local_accounts_email",
          columnNames = {"email"}),
      @UniqueConstraint(
          name = "uq_local_accounts_user_id",
          columnNames = {"user_id"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalAccountJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userId;
  private String email;
  private String passwordHash;

  // 가입 폼 스냅샷(nullable). users의 프로필 값과 별개 — 연동 시 users를 갱신하지 않아 두 값이 다를 수 있다.
  private String name;
  private LocalDate birthDate;

  // 무차별 대입 방어 상태. Redis TTL이 아니라 컬럼이라 시간 경과로 잠금이 풀리지 않는다(US-1-12).
  private int failedLoginAttempts;
  private Instant lockedAt;

  private Instant createdAt;
  private Instant updatedAt;
}
