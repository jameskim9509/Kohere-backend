package com.kohere.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kohere.auth.application.dto.LoginResult;
import com.kohere.auth.application.dto.TokenResponse;
import com.kohere.auth.domain.AccountLockedException;
import com.kohere.auth.domain.InvalidCredentialsException;
import com.kohere.auth.domain.LocalAccount;
import com.kohere.auth.domain.LocalAccountRepository;
import com.kohere.auth.domain.LoginAttemptRateLimiter;
import com.kohere.auth.domain.LoginRateLimitException;
import com.kohere.auth.domain.PasswordHasher;
import com.kohere.auth.presentation.dto.LoginRequest;
import com.kohere.user.api.UserAccountService;
import com.kohere.user.api.UserAccountView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link WebAuthService} 로그인 경로 단위 테스트(Mockito) — 계정 잠금 불변식과 게이트 순서를 고정한다.
 *
 * <p><b>왜 이 파일이 필요한가</b> — 잠금 로직은 「몇 번째 실패에 무엇을 쓰는가」·「잠긴 계정에는 무엇을 하지 <b>않는가</b>」·「무엇을 무엇보다 먼저 보는가」로
 * 이뤄져 있는데, 통합 테스트는 상태 코드만 보므로 <b>쓰지 않아야 할 것을 쓰지 않았는지</b>와 <b>호출 순서</b>를 볼 수 없다. 지금은 그 세 가지를 주석이 지키고
 * 있어, 순서를 바꾸는 리팩터링이 통합 테스트를 전부 통과한 채 들어올 수 있다.
 *
 * <p><b>여기서 볼 수 없는 것이 하나 있다</b> — 「로그인은 트랜잭션 단위가 아니다」는 불변식은 저장소를 목으로 바꾸면 롤백 자체가 사라져 관측할 수 없다. 그 하나만
 * {@code com.kohere.auth.WebLoginLockIntegrationTest}가 실제 DB로 검증한다.
 *
 * <p>가입 경로(게이트 순서·연동 분기)는 종단으로 확인해야 의미가 있어 문서·통합 테스트가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
class WebAuthServiceTest {

  private static final String EMAIL = "kim@work.example";
  private static final String PASSWORD = "Kohere1!";
  private static final String PASSWORD_HASH = "$2a$10$hash";
  private static final String CLIENT_IP = "203.0.113.10";
  private static final int MAX_FAILED_ATTEMPTS = 5;

  @Mock private SignupPhoneVerificationService signupPhoneVerificationService;
  @Mock private SignupEmailVerificationService signupEmailVerificationService;
  @Mock private LocalAccountRepository localAccountRepository;
  @Mock private PasswordHasher passwordHasher;
  @Mock private UserAccountService userAccountService;
  @Mock private AuthService authService;
  @Mock private LoginAttemptRateLimiter loginAttemptRateLimiter;

  private WebAuthService webAuthService;

  @BeforeEach
  void setUp() {
    AuthProperties authProperties = new AuthProperties();
    authProperties.getWeb().setLoginMaxFailedAttempts(MAX_FAILED_ATTEMPTS);
    webAuthService =
        new WebAuthService(
            signupPhoneVerificationService,
            signupEmailVerificationService,
            localAccountRepository,
            passwordHasher,
            userAccountService,
            authService,
            authProperties,
            loginAttemptRateLimiter);
  }

  @Test
  @DisplayName("연속 실패가 상한에 도달하면 카운터와 잠금 시각을 함께 기록한다")
  void login_reachingFailureLimit_recordsBothCounterAndLockedAt() {
    // 이미 4회 실패한(=상한 직전) 계정 — 다음 한 번이 5회째다.
    LocalAccount account = account(MAX_FAILED_ATTEMPTS - 1, null);
    when(localAccountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
    when(passwordHasher.matches(PASSWORD, PASSWORD_HASH)).thenReturn(false);

    assertThatThrownBy(() -> webAuthService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP))
        .isInstanceOf(InvalidCredentialsException.class);

    // 카운터만 오르고 lockedAt이 비어 있으면 잠금 코드는 그대로인데 아무도 잠기지 않는다 — 둘을 함께 단정한다.
    LocalAccount saved = captureSavedAccount();
    assertThat(saved.getFailedLoginAttempts()).isEqualTo(MAX_FAILED_ATTEMPTS);
    assertThat(saved.getLockedAt()).isNotNull();
    assertThat(saved.isLocked()).isTrue();
    // 실패 응답은 성공과 구분되지 않아야 하므로 토큰은 발급하지 않는다.
    verify(authService, never()).issueFullTokens(anyLong());
  }

  @Test
  @DisplayName("상한 직전까지의 실패는 카운터만 올리고 잠그지 않는다")
  void login_failureBeforeLimit_incrementsCounterWithoutLocking() {
    LocalAccount account = account(MAX_FAILED_ATTEMPTS - 2, null);
    when(localAccountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
    when(passwordHasher.matches(PASSWORD, PASSWORD_HASH)).thenReturn(false);

    assertThatThrownBy(() -> webAuthService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP))
        .isInstanceOf(InvalidCredentialsException.class);

    LocalAccount saved = captureSavedAccount();
    assertThat(saved.getFailedLoginAttempts()).isEqualTo(MAX_FAILED_ATTEMPTS - 1);
    assertThat(saved.getLockedAt()).isNull();
  }

  @Test
  @DisplayName("잠긴 계정은 비밀번호가 맞아도 423이고 카운터를 건드리지 않는다")
  void login_lockedAccount_rejectsEvenWithCorrectPasswordAndLeavesCounterUntouched() {
    LocalAccount locked = account(MAX_FAILED_ATTEMPTS, Instant.parse("2026-08-01T00:00:00Z"));
    when(localAccountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(locked));

    assertThatThrownBy(() -> webAuthService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP))
        .isInstanceOf(AccountLockedException.class);

    // 잠금 판정이 대조보다 먼저다 — 비밀번호를 아는 공격자에게도 잠긴 계정이 열리면 잠금이 막아 주는 것이 없어진다.
    verify(passwordHasher, never()).matches(anyString(), anyString());
    // 잠긴 계정에 실패를 더 세면 lockedAt이 요청마다 갱신돼 "언제 잠겼는가"가 사라진다.
    verify(localAccountRepository, never()).save(any());
    verify(authService, never()).issueFullTokens(anyLong());
  }

  @Test
  @DisplayName("로그인에 성공하면 실패 카운터를 0으로 되돌린다")
  void login_success_resetsFailedAttempts() {
    LocalAccount account = account(MAX_FAILED_ATTEMPTS - 2, null);
    when(localAccountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
    when(passwordHasher.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
    when(userAccountService.getAccount(7L))
        .thenReturn(new UserAccountView(7L, "ACTIVE", "김임대", "profile@work.example"));
    when(authService.issueFullTokens(7L))
        .thenReturn(new TokenResponse("Bearer", "access-token", "rt_raw", 3600));

    LoginResult result = webAuthService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP);

    LocalAccount saved = captureSavedAccount();
    assertThat(saved.getFailedLoginAttempts()).isZero();
    assertThat(saved.getLockedAt()).isNull();
    // refresh 원문은 쿠키로만 나가므로 응답 본문이 아니라 LoginResult가 들고 있다(ADR-0048).
    assertThat(result.refreshToken()).isEqualTo("rt_raw");
    assertThat(result.response().accessToken()).isEqualTo("access-token");
    // 표시 규칙 — 응답의 email·name은 로그인 ID가 아니라 users의 값이다.
    assertThat(result.response().email()).isEqualTo("profile@work.example");
    assertThat(result.response().name()).isEqualTo("김임대");
    assertThat(result.response().status()).isEqualTo("ACTIVE");
    assertThat(result.response().onboardingRequired()).isFalse();
  }

  @Test
  @DisplayName("ACTIVE가 아닌 계정은 비밀번호가 맞아도 401이고 토큰을 발급하지 않는다")
  void login_nonActiveAccount_throwsInvalidCredentialsAndIssuesNoTokens() {
    LocalAccount account = account(0, null);
    when(localAccountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
    when(passwordHasher.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
    // 탈퇴는 users 행을 지우지 않고 WITHDRAWN으로 익명화만 한다 — 자격증명이 남아 있으면 여기서 정식 세션이 나간다.
    when(userAccountService.getAccount(7L))
        .thenReturn(new UserAccountView(7L, "WITHDRAWN", null, null));

    assertThatThrownBy(() -> webAuthService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP))
        .isInstanceOf(InvalidCredentialsException.class);

    // 403·404로 가르면 "이 이메일은 실재하고 비밀번호도 맞다"까지 알려 주는 계정 열거 오라클이 된다.
    verify(authService, never()).issueFullTokens(anyLong());
  }

  @Test
  @DisplayName("시도 한도는 자격증명 조회·해시 대조보다 먼저 판정한다")
  void login_rateLimitExceeded_rejectsBeforeLookupAndHashing() {
    doThrow(new LoginRateLimitException())
        .when(loginAttemptRateLimiter)
        .recordAttempt(EMAIL, CLIENT_IP);

    assertThatThrownBy(() -> webAuthService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP))
        .isInstanceOf(LoginRateLimitException.class);

    // 한도를 뒤에서 보면 막힌 요청도 이미 BCrypt 비용을 치른 뒤라, 막으려던 CPU 증폭을 막지 못한다.
    // 「해시를 한 번도 부르지 않았다」가 그 순서의 유일한 관측 가능한 증거다.
    verifyNoInteractions(localAccountRepository, passwordHasher, userAccountService, authService);
  }

  @Test
  @DisplayName("정상 경로에서도 한도 → 조회 → 대조 순서를 지킨다")
  void login_invokesRateLimiterThenLookupThenHashComparison() {
    LocalAccount account = account(0, null);
    when(localAccountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
    when(passwordHasher.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
    when(userAccountService.getAccount(7L))
        .thenReturn(new UserAccountView(7L, "ACTIVE", "김임대", "profile@work.example"));
    when(authService.issueFullTokens(7L))
        .thenReturn(new TokenResponse("Bearer", "access-token", "rt_raw", 3600));

    webAuthService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP);

    // 예외 경로(위 테스트)만 보면 "한도 초과일 때만 먼저"일 수도 있다 — 순서 자체가 계약이라 성공 경로에서도 못박는다.
    InOrder order = inOrder(loginAttemptRateLimiter, localAccountRepository, passwordHasher);
    order.verify(loginAttemptRateLimiter).recordAttempt(EMAIL, CLIENT_IP);
    order.verify(localAccountRepository).findByEmail(EMAIL);
    order.verify(passwordHasher).matches(PASSWORD, PASSWORD_HASH);
  }

  @Test
  @DisplayName("등록되지 않은 이메일도 해시를 한 번 태우고 같은 401을 낸다")
  void login_unknownEmail_burnsOneHashAndThrowsSameError() {
    when(localAccountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> webAuthService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP))
        .isInstanceOf(InvalidCredentialsException.class);

    // 계정이 없을 때 해시를 건너뛰면 그 경로만 BCrypt 한 번만큼 빨라져 등록 여부를 읽어내는 타이밍 오라클이 된다.
    verify(passwordHasher).hash(PASSWORD);
    verify(localAccountRepository, never()).save(any());
  }

  private LocalAccount captureSavedAccount() {
    ArgumentCaptor<LocalAccount> captor = ArgumentCaptor.forClass(LocalAccount.class);
    verify(localAccountRepository).save(captor.capture());
    return captor.getValue();
  }

  private static LocalAccount account(int failedLoginAttempts, Instant lockedAt) {
    return LocalAccount.builder()
        .id(1L)
        .userId(7L)
        .email(EMAIL)
        .passwordHash(PASSWORD_HASH)
        .name("김임대")
        .birthDate(LocalDate.of(1990, 1, 1))
        .failedLoginAttempts(failedLoginAttempts)
        .lockedAt(lockedAt)
        .createdAt(Instant.parse("2026-07-01T00:00:00Z"))
        .updatedAt(Instant.parse("2026-07-01T00:00:00Z"))
        .build();
  }
}
