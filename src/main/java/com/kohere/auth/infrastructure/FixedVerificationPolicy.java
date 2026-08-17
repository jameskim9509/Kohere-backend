package com.kohere.auth.infrastructure;

import com.kohere.auth.application.EmailVerificationProperties;
import com.kohere.auth.application.PhoneVerificationProperties;
import com.kohere.auth.domain.Provider;
import com.kohere.auth.domain.SocialAccount;
import com.kohere.auth.domain.SocialAccountRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 고정 인증번호 적용 여부 판별(이슈 #180). 요청 주체가 <b>그 채널의 심사 계정</b>인지, 그리고 제출값이 그 그룹의 허용목록에 있는지를 함께 확인한다 — 어느
 * 한쪽만으로는 적용되지 않는다.
 *
 * <p>요청 주체는 JWT의 {@code userId}만 알 수 있으므로 {@link SocialAccountRepository#findAllByUserId}로 소셜 신원을
 * 되짚어 설정된 심사 계정과 대조한다. 심사 계정은 시드된 테스트 마스터가 아니라 <b>실제 Google 계정</b>이므로 {@code TestMasterAccount}와는
 * 무관한 경로다.
 *
 * <p><b>매핑은 여러 행일 수 있다</b> — 병합(US-1-15) 이후 한 회원에 Google·Apple 매핑이 함께 붙는 것이 정상이므로, 신원 대조는 <b>Google
 * 매핑 중 하나라도</b> 허용목록에 있으면 성립한다({@link #requesterGoogleEmails}). 첫 행만 보면 Apple이 먼저 잡힌 심사 계정이 조용히
 * 비심사로 판정돼 실제 발송으로 흘러간다.
 *
 * <p><b>역할별로 채널을 가둔다.</b> 임차인 그룹 계정은 이메일 인증만, 임대인 그룹 계정은 SMS 인증만 고정 인증번호를 받는다. 반대 채널 요청은 {@link
 * #isReviewAccount}가 참이므로 실제 발송으로 흘러가지 않고 거절된다 — 심사자는 외부 수신을 할 수 없어 흘려보내야 얻는 것이 없다.
 *
 * <p>이중 게이트({@code @Profile({"local","dev"})} + {@code app.auth.fixed-verification.enabled=true})로만
 * 등록되며, prod 프로파일엔 이 빈이 존재하지 않아 고정 인증번호 경로가 성립하지 않는다.
 */
@Slf4j
@Component
@Profile({"local", "dev"})
@ConditionalOnProperty(
    prefix = "app.auth.fixed-verification",
    name = "enabled",
    havingValue = "true")
@RequiredArgsConstructor
class FixedVerificationPolicy {

  private final SocialAccountRepository socialAccountRepository;
  private final FixedVerificationProperties properties;
  private final EmailVerificationProperties emailProperties;
  private final PhoneVerificationProperties phoneProperties;

  /**
   * 설정 정합성 검사 — 활성화됐는데 설정이 불완전하면 기동을 막는다. 잘못된 설정은 조용히 실제 발송 경로로 흘러가 심사자가 인증번호를 받지 못하는 상태가 되는데, 그걸
   * 심사 리젝으로 알게 되는 편보다 기동 실패가 낫다.
   *
   * <p>한쪽 트랙만 쓰는 운용은 허용한다(임차인 그룹만 채우고 임대인 그룹은 비워 두기). 다만 그룹을 <b>반만</b> 채우면(계정만 있고 대상이 없거나 그 반대) 주입
   * 실수이므로 막는다.
   */
  @PostConstruct
  void validateConfiguration() {
    validateCode();
    validateGroups();
    log.warn(
        "[FIXED VERIFICATION] 고정 인증번호 활성화 — 임차인 계정 {}건·임대인 계정 {}건. 운영 프로파일에서는 등록되지 않는다.",
        tenantGoogleEmails().size(),
        landlordGoogleEmails().size());
  }

  /**
   * 고정 인증번호는 숫자여야 하고, 자릿수가 {@code code-length}와 같아야 한다.
   *
   * <p>서버는 자릿수를 보지 않으므로(검증은 해시 대조뿐, 요청 DTO에도 길이 제약 없음) 길이가 달라도 <b>동작은 한다</b>. 막는 이유는 스펙이 "인증번호
   * 6자리"를 정책으로 명시하고 있어서다(docs/api/specs/01-auth-onboarding.md §4-1). 심사 계정을 "인증번호가 예측 가능한 정상 사용자"로
   * 두는 것이 이 기능의 전제인데, 자릿수만 다르면 그 전제가 깨진다. 자릿수 오타는 서버에서 멀쩡히 동작해 심사 중에야 드러나므로 기동 시점에 막는다.
   */
  private void validateCode() {
    String code = properties.getCode();
    if (!StringUtils.hasText(code) || !code.matches("\\d+")) {
      throw new IllegalStateException(
          "app.auth.fixed-verification.enabled=true 이면 code는 숫자로만 이루어진 값이어야 한다(현재 미설정 또는 형식 오류).");
    }
    if (code.length() != emailProperties.getCodeLength()
        || code.length() != phoneProperties.getCodeLength()) {
      throw new IllegalStateException(
          "app.auth.fixed-verification.code 자릿수(%d)가 인증번호 정책과 다르다 — app.email.code-length=%d, app.phone.code-length=%d."
              .formatted(
                  code.length(), emailProperties.getCodeLength(), phoneProperties.getCodeLength()));
    }
  }

  /** 그룹은 통째로 비어 있거나 통째로 채워져야 하고, 같은 Google 계정이 두 역할에 동시에 속할 수 없다. */
  private void validateGroups() {
    List<String> tenantAccounts = tenantGoogleEmails();
    List<String> landlordAccounts = landlordGoogleEmails();
    assertGroupComplete("tenant", tenantAccounts, tenantEmails(), "emails");
    assertGroupComplete("landlord", landlordAccounts, landlordPhoneNumbers(), "phone-numbers");
    if (tenantAccounts.isEmpty() && landlordAccounts.isEmpty()) {
      throw new IllegalStateException(
          "app.auth.fixed-verification.enabled=true 인데 tenant·landlord 어느 그룹에도 심사 계정이 없다 — 고정 인증번호가 적용될 요청이 없어 설정이 무의미하다.");
    }
    String overlapping =
        tenantAccounts.stream().filter(landlordAccounts::contains).findFirst().orElse(null);
    if (overlapping != null) {
      throw new IllegalStateException(
          "app.auth.fixed-verification의 google-email %s 가 tenant·landlord 양쪽에 있다 — 역할이 모호해 어느 채널을 허용할지 결정할 수 없다."
              .formatted(overlapping));
    }
  }

  /** 계정만 있고 대상이 없거나(또는 그 반대) 하면 주입 실수다 — 반쪽 설정은 조용히 실발송으로 이어진다. */
  private static void assertGroupComplete(
      String group, List<String> googleEmails, List<String> targets, String targetKey) {
    if (googleEmails.isEmpty() != targets.isEmpty()) {
      throw new IllegalStateException(
          "app.auth.fixed-verification.%s 설정이 반쪽이다 — google-emails(%d건)와 %s(%d건)는 함께 비거나 함께 채워야 한다."
              .formatted(group, googleEmails.size(), targetKey, targets.size()));
    }
  }

  /**
   * 이 요청이 고정 인증번호 대상인지 — 주체가 <b>임차인</b> 심사 계정이고, 제출 이메일이 임차인 그룹 허용목록에 있을 때만 true. 임대인 심사 계정이면 값과
   * 무관하게 false다(역할 밖 채널).
   */
  boolean appliesToEmail(long userId, String submittedEmail) {
    if (!isInGroup(userId, tenantGoogleEmails())) {
      return false;
    }
    String submitted = FixedVerificationProperties.normalizeEmail(submittedEmail);
    return submitted != null && tenantEmails().contains(submitted);
  }

  /**
   * 이 요청이 고정 인증번호 대상인지 — 주체가 <b>임대인</b> 심사 계정이고, 제출 번호가 임대인 그룹 허용목록에 있을 때만 true. 임차인 심사 계정이면 값과
   * 무관하게 false다(역할 밖 채널).
   */
  boolean appliesToPhone(long userId, String submittedPhoneNumber) {
    if (!isInGroup(userId, landlordGoogleEmails())) {
      return false;
    }
    String submitted = FixedVerificationProperties.normalizePhoneNumber(submittedPhoneNumber);
    return submitted != null && landlordPhoneNumbers().contains(submitted);
  }

  /**
   * 요청 주체가 심사 계정인지 — 역할·제출값을 보지 않고 두 그룹을 합쳐서 본다.
   *
   * <p>심사 계정이 역할 밖 채널을 쓰거나 허용목록에 없는 값을 제출했을 때 <b>실제 발송으로 흘려보내지 않고 거절</b>하기 위해 쓴다. 심사자는 외부 메일·SMS를
   * 수신할 수 없으므로 흘려보내면 "코드가 오지 않는" 막다른 길이 되고(SOLAPI 장애 시엔 500), 원인도 드러나지 않는다.
   */
  boolean isReviewAccount(long userId) {
    List<String> requesters = requesterGoogleEmails(userId);
    return requesters.stream().anyMatch(tenantGoogleEmails()::contains)
        || requesters.stream().anyMatch(landlordGoogleEmails()::contains);
  }

  /** 발급할 고정 인증번호. */
  String code() {
    return properties.getCode();
  }

  /** 요청 주체의 Google 매핑 중 <b>하나라도</b> 그룹 허용목록에 있으면 그 역할의 심사 계정으로 본다. */
  private boolean isInGroup(long userId, List<String> groupGoogleEmails) {
    return requesterGoogleEmails(userId).stream().anyMatch(groupGoogleEmails::contains);
  }

  /**
   * userId → 소셜 신원 → 정규화된 Google 계정 이메일 <b>전부</b>. Google 매핑이 없으면 빈 리스트다.
   *
   * <p>단건이 아닌 이유는 매핑이 단건이 아니기 때문이다 — 병합(US-1-15)으로 한 회원에 여러 provider 행이 붙을 수 있고, {@code user_id} 단건
   * 조회는 그 정상 상태에서 예외를 던진다. 여기서는 리스트를 그대로 받아 <b>Google 행만</b> 남긴다.
   */
  private List<String> requesterGoogleEmails(long userId) {
    return socialAccountRepository.findAllByUserId(userId).stream()
        .filter(socialAccount -> socialAccount.getProvider() == Provider.GOOGLE)
        .map(SocialAccount::getEmail)
        .map(FixedVerificationProperties::normalizeEmail)
        .filter(Objects::nonNull)
        .toList();
  }

  private List<String> tenantGoogleEmails() {
    return FixedVerificationProperties.normalizedEmails(properties.getTenant().getGoogleEmails());
  }

  private List<String> tenantEmails() {
    return FixedVerificationProperties.normalizedEmails(properties.getTenant().getEmails());
  }

  private List<String> landlordGoogleEmails() {
    return FixedVerificationProperties.normalizedEmails(properties.getLandlord().getGoogleEmails());
  }

  private List<String> landlordPhoneNumbers() {
    return FixedVerificationProperties.normalizedPhoneNumbers(
        properties.getLandlord().getPhoneNumbers());
  }
}
