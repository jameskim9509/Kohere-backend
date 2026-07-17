package com.kohere.user.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사용자 차단 JPA 엔티티(내부, MySQL {@code user_blocks}). 도메인 {@link com.kohere.user.domain.UserBlock}과 분리해
 * 영속 매핑만 담당한다. 컬럼명은 기본 snake_case 매핑을 따른다(blockerId→blocker_id 등). 스키마는 Flyway {@code
 * V15__create_user_blocks.sql}.
 */
@Entity
@Table(name = "user_blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBlockJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long blockerId;
  private Long blockedUserId;
  private Instant createdAt;
}
