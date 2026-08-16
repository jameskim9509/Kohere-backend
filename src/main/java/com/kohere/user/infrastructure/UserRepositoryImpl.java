package com.kohere.user.infrastructure;

import com.kohere.user.domain.User;
import com.kohere.user.domain.UserRepository;
import com.kohere.user.domain.UserStatus;
import com.kohere.user.domain.UserType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 회원 영속 어댑터. 도메인 포트 {@link UserRepository}를 JPA로 구현하고 도메인↔엔티티를 매핑한다 (docs/convention/code-style.md
 * §3-3, 의존성 역전).
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

  private final UserJpaRepository jpaRepository;

  @Override
  public Optional<User> findById(Long id) {
    return jpaRepository.findById(id).map(UserRepositoryImpl::toDomain);
  }

  /**
   * 잠금 조회는 엔티티를 읽고 식별자만 꺼내 돌려준다 — 포트가 {@code Optional<Long>}인 이유는 {@link
   * UserRepository#findActiveLandlordIdByPhoneNumberForUpdate} 참조. 조건의 {@code ACTIVE}·{@code
   * LANDLORD}는 여기서 상수로 고정한다(호출자가 바꿀 수 있는 값이 아니라 연동 규칙이다).
   */
  @Override
  public Optional<Long> findActiveLandlordIdByPhoneNumberForUpdate(String phoneNumber) {
    return jpaRepository
        .findByPhoneNumberAndStatusAndUserType(phoneNumber, UserStatus.ACTIVE, UserType.LANDLORD)
        .map(UserJpaEntity::getId);
  }

  @Override
  public boolean existsByNickname(String nickname) {
    return jpaRepository.existsByNickname(nickname);
  }

  @Override
  public User save(User user) {
    return toDomain(jpaRepository.save(toEntity(user)));
  }

  private static User toDomain(UserJpaEntity e) {
    return User.builder()
        .id(e.getId())
        .name(e.getName())
        .nickname(e.getNickname())
        .gender(e.getGender())
        .birthDate(e.getBirthDate())
        .country(e.getCountry())
        .lang(e.getLang())
        .occupation(e.getOccupation())
        .email(e.getEmail())
        .visaType(e.getVisaType())
        .userType(e.getUserType())
        .phoneNumber(e.getPhoneNumber())
        .businessRegistrationNumberHash(e.getBusinessRegistrationNumberHash())
        .status(e.getStatus())
        .termsOfServiceAgreed(e.isTermsOfServiceAgreed())
        .privacyPolicyAgreed(e.isPrivacyPolicyAgreed())
        .marketingAgreed(e.isMarketingAgreed())
        .termsVersion(e.getTermsVersion())
        .agreedAt(e.getAgreedAt())
        .createdAt(e.getCreatedAt())
        .updatedAt(e.getUpdatedAt())
        .withdrawnAt(e.getWithdrawnAt())
        .build();
  }

  private static UserJpaEntity toEntity(User u) {
    return UserJpaEntity.builder()
        .id(u.getId())
        .name(u.getName())
        .nickname(u.getNickname())
        .gender(u.getGender())
        .birthDate(u.getBirthDate())
        .country(u.getCountry())
        .lang(u.getLang())
        .occupation(u.getOccupation())
        .email(u.getEmail())
        .visaType(u.getVisaType())
        .userType(u.getUserType())
        .phoneNumber(u.getPhoneNumber())
        .businessRegistrationNumberHash(u.getBusinessRegistrationNumberHash())
        .status(u.getStatus())
        .termsOfServiceAgreed(u.isTermsOfServiceAgreed())
        .privacyPolicyAgreed(u.isPrivacyPolicyAgreed())
        .marketingAgreed(u.isMarketingAgreed())
        .termsVersion(u.getTermsVersion())
        .agreedAt(u.getAgreedAt())
        .createdAt(u.getCreatedAt())
        .updatedAt(u.getUpdatedAt())
        .withdrawnAt(u.getWithdrawnAt())
        .build();
  }
}
