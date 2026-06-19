package com.kohere.user.application;

import com.kohere.common.exception.InvalidInputException;
import com.kohere.user.api.UserWithdrawnEvent;
import com.kohere.user.application.dto.UserProfileResponse;
import com.kohere.user.domain.Country;
import com.kohere.user.domain.CountryRepository;
import com.kohere.user.domain.User;
import com.kohere.user.domain.UserNotFoundException;
import com.kohere.user.domain.UserRepository;
import com.kohere.user.domain.UserStatus;
import com.kohere.user.presentation.dto.UpdateProfileRequest;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 프로필·계정 lifecycle 유스케이스(/users/me). 인증 주체(userId)는 컨트롤러가 SecurityContext에서 받아 전달한다.
 *
 * <p>국적은 {@code country}(ISO 코드)만 저장하고 표시명·국기는 {@link CountryRepository}로 resolve한다. 탈퇴는 WITHDRAWN
 * 전이 + PII 즉시 익명화(도메인) 후 {@link UserWithdrawnEvent}를 발행해 auth가 social_accounts 삭제·refresh 무효화를
 * 수행하도록 한다(ADR-0002/0014).
 */
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final CountryRepository countryRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public UserProfileResponse getMyProfile(long userId) {
    return toResponse(activeUser(userId));
  }

  @Transactional
  public UserProfileResponse updateMyProfile(long userId, UpdateProfileRequest request) {
    User user = activeUser(userId);
    if (request.country() != null && !countryRepository.existsByCode(request.country())) {
      throw new InvalidInputException("country 값이 올바르지 않습니다: " + request.country());
    }
    User updated =
        user.updateProfile(
            request.firstName(),
            request.lastName(),
            request.gender(),
            request.birthDate(),
            request.country(),
            request.occupation(),
            request.visaType(),
            request.marketingAgreed(),
            Instant.now());
    return toResponse(userRepository.save(updated));
  }

  @Transactional
  public void withdraw(long userId) {
    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    userRepository.save(user.withdraw(Instant.now()));
    eventPublisher.publishEvent(new UserWithdrawnEvent(userId));
  }

  private User activeUser(long userId) {
    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    if (user.getStatus() == UserStatus.WITHDRAWN) {
      throw new UserNotFoundException();
    }
    return user;
  }

  private UserProfileResponse toResponse(User u) {
    Country country =
        u.getCountry() == null ? null : countryRepository.findByCode(u.getCountry()).orElse(null);
    return new UserProfileResponse(
        u.getId(),
        u.getFirstName(),
        u.getLastName(),
        u.getNickname(),
        u.getGender(),
        u.getBirthDate(),
        u.getCountry(),
        country == null ? null : country.name(),
        country == null ? null : country.flag(),
        u.getOccupation(),
        u.getEmail(),
        u.getVisaType(),
        u.getStatus(),
        u.isTermsOfServiceAgreed(),
        u.isPrivacyPolicyAgreed(),
        u.isMarketingAgreed(),
        u.getCreatedAt());
  }
}
