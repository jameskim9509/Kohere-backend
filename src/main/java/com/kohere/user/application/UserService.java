package com.kohere.user.application;

import com.kohere.user.api.UserWithdrawnEvent;
import com.kohere.user.application.dto.UserProfileResponse;
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
 * <p>탈퇴는 WITHDRAWN 전이 + PII 즉시 익명화(도메인) 후 {@link UserWithdrawnEvent}를 발행해 auth가 social_accounts
 * 삭제·refresh 무효화를 수행하도록 한다(ADR-0002/0014).
 */
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public UserProfileResponse getMyProfile(long userId) {
    return toResponse(activeUser(userId));
  }

  @Transactional
  public UserProfileResponse updateMyProfile(long userId, UpdateProfileRequest request) {
    User user = activeUser(userId);
    User updated =
        user.updateProfile(
            request.firstName(),
            request.lastName(),
            request.gender(),
            request.birthDate(),
            request.countryCode(),
            request.phoneNumber(),
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

  private static UserProfileResponse toResponse(User u) {
    return new UserProfileResponse(
        u.getId(),
        u.getFirstName(),
        u.getLastName(),
        u.getGender(),
        u.getBirthDate(),
        u.getCountryCode(),
        u.getPhoneNumber(),
        u.getVisaType(),
        u.getStatus(),
        u.isTermsOfServiceAgreed(),
        u.isPrivacyPolicyAgreed(),
        u.isMarketingAgreed(),
        u.getCreatedAt());
  }
}
