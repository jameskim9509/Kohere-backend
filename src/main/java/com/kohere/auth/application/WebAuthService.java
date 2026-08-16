package com.kohere.auth.application;

import com.kohere.auth.application.dto.SignupResponse;
import com.kohere.auth.application.dto.SignupResult;
import com.kohere.auth.application.dto.TokenResponse;
import com.kohere.auth.domain.EmailAlreadyRegisteredException;
import com.kohere.auth.domain.LocalAccount;
import com.kohere.auth.domain.LocalAccountRepository;
import com.kohere.auth.domain.PasswordHasher;
import com.kohere.auth.domain.RequiredAgreementMissingException;
import com.kohere.auth.domain.WebAccountAlreadyExistsException;
import com.kohere.auth.presentation.dto.SignupRequest;
import com.kohere.common.request.PhoneNumbers;
import com.kohere.common.request.RequestDates;
import com.kohere.user.api.LandlordOnboardingProfile;
import com.kohere.user.api.UserAccountService;
import com.kohere.user.api.UserAccountView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 임대인 웹(로컬 자격증명) 인증 유스케이스 — 회원가입(US-1-11). 소셜이 아니라 <b>이메일 + 비밀번호</b>로 들어오는 별개 진입점이며, 가입은 "계정 생성"이
 * 아니라 <b>"자격증명 추가"</b>다(ADR-0047 §2).
 *
 * <p><b>왜 {@link AuthService}에 넣지 않는가</b> — {@code AuthService}는 이미 소셜 로그인·약관·이메일/연락처 인증·온보딩·재발급을
 * 들고 있고, 협력자가 열 개에 가깝다. 여기에 자격증명 해시·잠금 카운터·쿠키 채널이 얹히면 "소셜로 시작하는 계정 생애주기"라는 한 줄 설명이 깨진다. 반대로 <b>토큰
 * 발급은 나눠 갖지 않는다</b> — {@link AuthService#issueFullTokens}를 그대로 부른다(ADR-0048 §3: 회전·재사용 탐지 규칙을 두 벌로
 * 만들지 않는다).
 *
 * <p><b>가입 전체가 한 트랜잭션이다.</b> 웹에는 온보딩 재개 화면이 없어 {@code PENDING}·{@code TERMS_AGREED} 같은 부분 완료 상태를
 * 남기면 로그인해도 갈 곳이 없는 죽은 계정이 된다. 그래서 상태 체인은 앱과 똑같이 태우되(앱 계정과 데이터 모양이 같아야 연동이 성립한다) 한 트랜잭션 안에서 {@code
 * ACTIVE}까지 연속 전이시킨다 — 어느 단계에서 실패해도 {@code users}만 있고 자격증명이 없는(로그인 불가) 계정도, 그 반대도 남지 않는다.
 *
 * <p><b>user 모듈에 새 생성 메서드를 만들지 않는다.</b> 기존 세 메서드({@code createPendingUser} → {@code agreeToTerms} →
 * {@code completeLandlordOnboarding})를 순서대로 부르면 {@code @Transactional(REQUIRED)} 전파로 이 트랜잭션에 참여해
 * 원자성이 그대로 성립한다. 웹 전용 생성 경로를 만들면 앱과 데이터 모양이 갈라질 여지만 생긴다.
 *
 * <p>docs/api/specs/01-auth-onboarding.md §1-3 · 시퀀스 us-1-11-web-signup.
 */
@Service
@RequiredArgsConstructor
public class WebAuthService {

  /** 웹 계정은 한 트랜잭션으로 완주하므로 응답 상태가 갈리지 않는다 — 항상 ACTIVE다. */
  private static final String STATUS_ACTIVE = "ACTIVE";

  /** 웹 가입에는 온보딩 재개 분기가 없다 — 항상 false다. */
  private static final boolean ONBOARDING_REQUIRED = false;

  private final SignupPhoneVerificationService signupPhoneVerificationService;
  private final LocalAccountRepository localAccountRepository;
  private final PasswordHasher passwordHasher;
  private final UserAccountService userAccountService;
  private final AuthService authService;

  /**
   * 임대인 웹 회원가입. 게이트를 <b>인증 마커 → 필수 약관 → 이메일 중복 → 번호 매칭</b> 순서로 통과시킨 뒤 연동(자격증명만 추가) 또는 신규 생성(상태 체인
   * 완주)으로 갈린다. 실패는 전부 롤백된다.
   *
   * <p><b>게이트 순서는 계약이다</b>(스펙 §1-3의 검증 게이트 우선순위) — 인증 마커를 가장 먼저 보는 이유는 번호가 비밀이 아니기 때문이다. 마커 없이 이메일
   * 중복이나 번호 매칭을 먼저 판정하면, 남의 번호를 아는 사람이 응답 코드만으로 "그 번호에 계정이 있는지"를 읽어낼 수 있다.
   *
   * <p><b>연동 판정 키는 번호 단독</b>이고 이름은 조건이 아니다 — 소유 증명은 전적으로 SMS 인증이 담당하므로 이름을 더해도 막히는 공격은 없는 반면, 앱
   * 이름(소셜 SDK 표기)과 웹 이름(직접 입력)의 자연스러운 불일치로 <b>계정이 조용히 갈라진다</b>(ADR-0047 §3).
   *
   * @return 응답 본문과, 쿠키로만 내려갈 refresh 원문({@link SignupResult})
   */
  @Transactional
  public SignupResult signup(SignupRequest request) {
    // 번호는 여기서 한 번 접고 이후 마커 조회·계정 매칭·users 저장이 모두 같은 표준형을 쓴다(#229 D10).
    String phoneNumber = PhoneNumbers.normalize(request.phoneNumber());
    // 형식 검증은 게이트보다 먼저다 — 부수효과가 없고, 400(형식)과 422(비즈니스 규칙)가 뒤바뀌면 클라이언트가
    // "인증부터 다시 하라"는 안내를 형식 오류에 띄운다. birthDate만 Bean Validation이 못 보는 형식이라 여기서 판정한다.
    LocalDate birthDate = RequestDates.parsePast("birthDate", request.birthDate());

    signupPhoneVerificationService.assertVerified(phoneNumber);
    assertRequiredAgreements(request);
    // 로그인 ID 유일성만 본다 — users.email은 보지 않는다. 임대인 대다수가 소셜과 같은 이메일로 가입하므로
    // 거기까지 유일하게 걸면 본인이 본인 이메일로 가입하다 409를 맞는다(ADR-0047 §6).
    if (localAccountRepository.existsByEmail(request.email())) {
      throw new EmailAlreadyRegisteredException();
    }

    // 여기서부터 두 갈래다 — 같은 조회의 서로 다른 가지이며 응답의 linked가 그 결과를 그대로 나른다.
    Optional<Long> matched = userAccountService.findActiveLandlordIdByPhoneNumber(phoneNumber);
    boolean linked = matched.isPresent();
    long userId =
        linked ? linkExisting(matched.get()) : createLandlord(request, phoneNumber, birthDate);

    Instant now = Instant.now();
    localAccountRepository.save(
        LocalAccount.register(
            userId,
            request.email(),
            passwordHasher.hash(request.password()),
            request.name(),
            birthDate,
            now));

    // 표시 규칙 — 응답의 name·email은 언제나 users의 값이다. 연동 경로에서는 폼 값이 아니라 소셜 진본이 나간다(의도된 동작).
    UserAccountView account = userAccountService.getAccount(userId);
    TokenResponse tokens = authService.issueFullTokens(userId);
    consumeVerificationAfterCommit(phoneNumber);

    return new SignupResult(
        new SignupResponse(
            linked,
            ONBOARDING_REQUIRED,
            STATUS_ACTIVE,
            tokens.tokenType(),
            tokens.accessToken(),
            tokens.expiresIn(),
            account.email(),
            account.name()),
        tokens.refreshToken());
  }

  /**
   * 인증 마커 소비를 <b>커밋 이후로</b> 미룬다. Redis 삭제는 이 트랜잭션과 함께 롤백되지 않으므로, 트랜잭션 안에서 지우면 커밋 시점에 터지는 실패(제약 위반
   * 플러시·커넥션 단절)가 MySQL 쪽만 되돌리고 마커는 이미 사라진 상태를 남긴다 — 사용자는 만들어지지도 않은 계정 때문에 500을 받고, 재시도하면 422 {@code
   * AUTH_PHONE_NOT_VERIFIED}로 SMS 인증부터 다시 해야 한다.
   *
   * <p>반대 방향(계정은 커밋됐는데 마커가 남음)은 애초에 무해하다 — 마커 재사용으로 할 수 있는 일은 같은 번호로 또 가입하는 것뿐이고 그건 409로 막힌다. 그래서
   * <b>"커밋된 뒤에만 지운다"</b>가 양쪽을 다 만족시키는 유일한 순서다.
   */
  private void consumeVerificationAfterCommit(String phoneNumber) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      // 트랜잭션 밖 호출(테스트 등) — 미룰 커밋이 없으므로 즉시 소비한다.
      signupPhoneVerificationService.consumeVerification(phoneNumber);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            signupPhoneVerificationService.consumeVerification(phoneNumber);
          }
        });
  }

  /**
   * 필수 약관 게이트. {@code @NotNull}은 필드의 <b>존재</b>만 강제하고 {@code false}는 형식이 아니라 비즈니스 규칙 위반이라 400이 아닌
   * 422다 — 앱의 {@code POST /auth/terms}(US-1-7)와 같은 규칙·같은 코드를 쓴다(#229 D16).
   */
  private static void assertRequiredAgreements(SignupRequest request) {
    if (!Boolean.TRUE.equals(request.termsOfServiceAgreed())
        || !Boolean.TRUE.equals(request.privacyPolicyAgreed())) {
      throw new RequiredAgreementMissingException();
    }
  }

  /**
   * 연동 경로 — 번호로 찾은 기존 계정에 <b>자격증명을 붙일 자리가 비어 있는지</b>만 확인하고 그 {@code userId}를 그대로 쓴다.
   *
   * <p>{@code users}는 <b>한 칼럼도 건드리지 않는다</b>. 폼의 이름·생년월일·이메일은 방금 입력한 미검증 값이고 기존 값은 온보딩을 마친 확정 값이라,
   * 덮어쓰면 "가입했더니 내 프로필이 바뀌었다"가 된다(ADR-0047 §6). 폼 값은 {@code local_accounts} 스냅샷에만 남는다.
   *
   * <p>자리가 이미 찼으면 409다 — 남은 동작은 기존 자격증명 덮어쓰기뿐인데 그건 가입이 아니라 로그인 ID까지 조용히 바꾸는 <b>자격증명 교체</b>다. 응답에는 그
   * 계정의 이메일을 마스킹해서도 싣지 않는다(#229 D4 — 공통 에러 스키마의 code·message만).
   */
  private long linkExisting(long userId) {
    if (localAccountRepository.existsByUserId(userId)) {
      throw new WebAccountAlreadyExistsException();
    }
    return userId;
  }

  /**
   * 신규 경로 — 앱과 <b>같은 도메인 메서드를 같은 순서로</b> 불러 {@code PENDING → TERMS_AGREED → ACTIVE}를 연속 전이시킨다. 서버
   * 고정값({@code country=KR}·{@code lang=ko}·닉네임 자동 배정·{@code userType=LANDLORD})도 앱과 같다.
   *
   * <p>신규 생성일 때만 폼의 이름·이메일이 {@code users}에도 들어간다(연동 경로와의 유일한 차이 — #229 D7). 정규화한 번호를 {@code
   * users.phone_number}에 남기는 것이 특히 중요하다: 그래야 반대 방향(앱 온보딩, US-1-15)에서 이 계정이 병합 후보로 잡힌다.
   */
  private long createLandlord(SignupRequest request, String phoneNumber, LocalDate birthDate) {
    long userId = userAccountService.createPendingUser(request.name(), request.email());
    userAccountService.agreeToTerms(userId, Boolean.TRUE.equals(request.marketingAgreed()));
    userAccountService.completeLandlordOnboarding(
        userId, new LandlordOnboardingProfile(phoneNumber, birthDate));
    return userId;
  }
}
