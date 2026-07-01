package com.kohere.user.application;

import com.kohere.common.exception.InvalidInputException;
import com.kohere.user.api.LandlordOnboardingProfile;
import com.kohere.user.api.OnboardingProfile;
import com.kohere.user.api.TermsAgreementView;
import com.kohere.user.api.UserAccountService;
import com.kohere.user.api.UserAccountView;
import com.kohere.user.api.UserProfileView;
import com.kohere.user.domain.Country;
import com.kohere.user.domain.CountryRepository;
import com.kohere.user.domain.Gender;
import com.kohere.user.domain.NicknameGenerator;
import com.kohere.user.domain.Occupation;
import com.kohere.user.domain.User;
import com.kohere.user.domain.UserNotFoundException;
import com.kohere.user.domain.UserRepository;
import com.kohere.user.domain.VisaType;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * user 공개 API 구현. auth가 호출하는 회원 생성·약관 동의·온보딩 완료·계정 조회를 처리한다. 약관 버전은 서버 설정값(app.terms.version)을 약관
 * 동의 시 기록한다(ADR-0012). gender·occupation·visaType은 원시 문자열로 받아 enum으로 변환하고(유효하지 않으면 INVALID_INPUT),
 * country는 {@link CountryRepository}로 존재를 검증한다. 닉네임은 {@link NicknameGenerator}로 생성한다.
 */
@Service
public class UserAccountServiceImpl implements UserAccountService {

  private static final String DEFAULT_LANGUAGE = "en";

  private final UserRepository userRepository;
  private final CountryRepository countryRepository;
  private final NicknameGenerator nicknameGenerator;
  private final String termsVersion;

  public UserAccountServiceImpl(
      UserRepository userRepository,
      CountryRepository countryRepository,
      NicknameGenerator nicknameGenerator,
      @Value("${app.terms.version}") String termsVersion) {
    this.userRepository = userRepository;
    this.countryRepository = countryRepository;
    this.nicknameGenerator = nicknameGenerator;
    this.termsVersion = termsVersion;
  }

  @Override
  @Transactional
  public long createPendingUser() {
    return userRepository.save(User.createPending(Instant.now())).getId();
  }

  @Override
  @Transactional
  public TermsAgreementView agreeToTerms(long userId, boolean marketingAgreed) {
    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    User saved =
        userRepository.save(user.agreeToTerms(marketingAgreed, termsVersion, Instant.now()));
    return new TermsAgreementView(
        saved.getStatus().name(),
        saved.isTermsOfServiceAgreed(),
        saved.isPrivacyPolicyAgreed(),
        saved.isMarketingAgreed(),
        saved.getAgreedAt());
  }

  @Override
  @Transactional
  public UserProfileView completeOnboarding(long userId, OnboardingProfile profile) {
    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    Gender gender = parseEnum(Gender.class, profile.gender());
    Occupation occupation = parseEnum(Occupation.class, profile.occupation());
    VisaType visaType = parseEnum(VisaType.class, profile.visaType());
    if (profile.country() == null || !countryRepository.existsByCode(profile.country())) {
      throw new InvalidInputException("country 값이 올바르지 않습니다: " + profile.country());
    }
    String nickname = nicknameGenerator.generateUnique();
    User active =
        user.completeOnboarding(
            profile.firstName(),
            profile.lastName(),
            nickname,
            gender,
            profile.birthDate(),
            profile.country(),
            occupation,
            profile.email(),
            visaType,
            Instant.now());
    return toProfileView(userRepository.save(active));
  }

  @Override
  @Transactional
  public UserProfileView completeLandlordOnboarding(
      long userId, LandlordOnboardingProfile profile) {
    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    String nickname = nicknameGenerator.generateUnique();
    User active =
        user.completeLandlordOnboarding(
            profile.name(), profile.phoneNumber(), nickname, Instant.now());
    return toProfileView(userRepository.save(active));
  }

  @Override
  @Transactional(readOnly = true)
  public UserAccountView getAccount(long userId) {
    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    return new UserAccountView(user.getId(), user.getStatus().name());
  }

  @Override
  @Transactional(readOnly = true)
  public String getUserType(long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(UserNotFoundException::new)
        .getUserType()
        .name();
  }

  @Override
  @Transactional(readOnly = true)
  public String getLanguage(long userId) {
    String country =
        userRepository.findById(userId).orElseThrow(UserNotFoundException::new).getCountry();
    if (!StringUtils.hasText(country)) {
      return DEFAULT_LANGUAGE;
    }
    return countryRepository
        .findByCode(country)
        .map(Country::lang)
        .filter(StringUtils::hasText)
        .orElse(DEFAULT_LANGUAGE);
  }

  private UserProfileView toProfileView(User u) {
    // 임대인은 country·gender·occupation·visaType 미수집(null) — 세입자/임대인 공용이라 null 가드한다.
    Country country =
        u.getCountry() == null ? null : countryRepository.findByCode(u.getCountry()).orElse(null);
    return new UserProfileView(
        u.getId(),
        u.getFirstName(),
        u.getLastName(),
        u.getNickname(),
        u.getGender() == null ? null : u.getGender().name(),
        u.getBirthDate(),
        u.getCountry(),
        country == null ? null : country.name(),
        country == null ? null : country.flag(),
        u.getOccupation() == null ? null : u.getOccupation().name(),
        u.getEmail(),
        u.getVisaType() == null ? null : u.getVisaType().name(),
        u.getUserType() == null ? null : u.getUserType().name(),
        u.getPhoneNumber(),
        u.getStatus().name(),
        u.isMarketingAgreed(),
        u.getCreatedAt());
  }

  private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidInputException(type.getSimpleName() + " 값이 올바르지 않습니다: " + value);
    }
  }
}
