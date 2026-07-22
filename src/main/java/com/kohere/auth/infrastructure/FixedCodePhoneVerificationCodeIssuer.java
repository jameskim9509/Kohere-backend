package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.PhoneVerificationCodeIssuer;
import com.kohere.auth.domain.PhoneVerificationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * dev·local 전용 고정 SMS 인증번호 발급 래퍼(이슈 #180, {@link FixedCodeEmailVerificationCodeIssuer}와 대칭). 심사 계정이
 * 등록된 번호를 인증할 때만 고정 인증번호를 반환하고 <b>SMS 발송을 생략</b>하며, 그 외 모든 요청은 실제 발급기({@link
 * PhoneVerificationCodeIssuerImpl})에 위임한다 — 일반 임대인의 연락처 인증은 그대로 동작한다.
 *
 * <p>발송을 아예 호출하지 않으므로 SOLAPI 활성화 여부·장애와 무관하게 심사 흐름이 성립한다.
 *
 * <p><b>심사 계정이 설정에 없는 번호를 제출하면 실제 발송으로 흘려보내지 않고 거절한다.</b> 흘려보내면 코드가 오지 않을 뿐 아니라 SOLAPI SDK 충돌 시
 * 500까지 노출된다. {@link PhoneVerificationFailedException}(422) 재사용의 문구 부정확성은 {@link
 * FixedCodeEmailVerificationCodeIssuer} 설명 참고.
 *
 * <p>이중 게이트({@code @Profile({"local","dev"})} + {@code app.auth.fixed-verification.enabled=true})로만
 * 등록된다.
 */
@Slf4j
@Component
@Primary
@Profile({"local", "dev"})
@ConditionalOnProperty(
    prefix = "app.auth.fixed-verification",
    name = "enabled",
    havingValue = "true")
@RequiredArgsConstructor
class FixedCodePhoneVerificationCodeIssuer implements PhoneVerificationCodeIssuer {

  private final PhoneVerificationCodeIssuerImpl delegate;
  private final FixedVerificationPolicy policy;

  @Override
  public String issue(long userId, String phoneNumber) {
    if (policy.appliesToPhone(userId, phoneNumber)) {
      log.warn("[FIXED VERIFICATION] 심사 계정 연락처 인증 — 고정 인증번호 발급, SMS 미발송 (userId={}).", userId);
      return policy.code();
    }
    if (policy.isReviewAccount(userId)) {
      // 심사자는 외부 SMS를 수신할 수 없다 — 흘려보내면 코드가 오지 않고, SOLAPI 장애 시엔 500까지 본다.
      log.warn(
          "[FIXED VERIFICATION] 심사 계정이 설정에 없는 번호로 인증을 요청했다 — 실제 발송을 막고 거절한다 (userId={}). "
              + "app.auth.fixed-verification.accounts에 등록된 값과 심사 노트가 일치하는지 확인할 것.",
          userId);
      throw new PhoneVerificationFailedException();
    }
    return delegate.issue(userId, phoneNumber);
  }
}
