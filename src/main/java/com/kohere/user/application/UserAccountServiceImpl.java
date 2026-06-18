package com.kohere.user.application;

import com.kohere.common.exception.InvalidInputException;
import com.kohere.user.api.OnboardingProfile;
import com.kohere.user.api.UserAccountService;
import com.kohere.user.api.UserAccountView;
import com.kohere.user.api.UserProfileView;
import com.kohere.user.domain.Gender;
import com.kohere.user.domain.User;
import com.kohere.user.domain.UserNotFoundException;
import com.kohere.user.domain.UserRepository;
import com.kohere.user.domain.VisaType;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * user 공개 API 구현. auth가 호출하는 회원 생성·온보딩 완료·계정 조회를 처리한다. 약관 버전은 서버 설정값(app.terms.version)을 온보딩 완료 시
 * 기록한다(ADR-0012). gender·visaType은 원시 문자열로 받아 enum으로 변환한다(유효하지 않으면 INVALID_INPUT).
 */
@Service
public class UserAccountServiceImpl implements UserAccountService {

  private final UserRepository userRepository;
  private final String termsVersion;

  public UserAccountServiceImpl(
      UserRepository userRepository, @Value("${app.terms.version}") String termsVersion) {
    this.userRepository = userRepository;
    this.termsVersion = termsVersion;
  }

  @Override
  @Transactional
  public long createPendingUser() {
    return userRepository.save(User.createPending(Instant.now())).getId();
  }

  @Override
  @Transactional
  public UserProfileView completeOnboarding(long userId, OnboardingProfile profile) {
    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    Gender gender = parseEnum(Gender.class, profile.gender());
    VisaType visaType = parseEnum(VisaType.class, profile.visaType());
    User active =
        user.completeOnboarding(
            profile.firstName(),
            profile.lastName(),
            gender,
            profile.birthDate(),
            profile.countryCode(),
            profile.phoneNumber(),
            visaType,
            profile.marketingAgreed(),
            termsVersion,
            Instant.now());
    User saved = userRepository.save(active);
    return new UserProfileView(
        saved.getId(),
        saved.getFirstName(),
        saved.getLastName(),
        saved.getGender().name(),
        saved.getBirthDate(),
        saved.getCountryCode(),
        saved.getPhoneNumber(),
        saved.getVisaType().name(),
        saved.getStatus().name(),
        saved.isMarketingAgreed(),
        saved.getCreatedAt());
  }

  @Override
  @Transactional(readOnly = true)
  public UserAccountView getAccount(long userId) {
    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    return new UserAccountView(user.getId(), user.getStatus().name());
  }

  private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidInputException(type.getSimpleName() + " 값이 올바르지 않습니다: " + value);
    }
  }
}
