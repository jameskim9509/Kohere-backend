package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.Provider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 소셜 자격 매핑 JPA 엔티티(MySQL {@code social_accounts}). (provider, provider_user_id) 전역 유니크 — 로그인 분기 등치
 * 조회·중복 연동 방지. userId는 user 소유라 FK 미설정(값 참조, ADR-0002/0005). 도메인과 분리된 영속 모델로 어댑터가 매핑한다.
 */
@Entity
@Table(
    name = "social_accounts",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_social_accounts_provider_user",
            columnNames = {"provider", "provider_user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialAccountJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  private Provider provider;

  @Column(name = "provider_user_id")
  private String providerUserId;

  private String email;

  // provider가 준 표시 이름 스냅샷(로그인마다 갱신, Apple name은 최초값 유지). User.name(사용자 값)과 별개(#192).
  private String name;

  private Long userId;
  private Instant linkedAt;

  // Apple refresh token(폐기용, ADR-0031). nullable·1급 시크릿 — @ToString 미적용으로 로그 노출 차단.
  @Column(name = "apple_refresh_token")
  private String appleRefreshToken;
}
