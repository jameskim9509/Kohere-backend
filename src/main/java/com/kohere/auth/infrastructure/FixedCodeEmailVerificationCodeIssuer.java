package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.EmailVerificationCodeIssuer;
import com.kohere.auth.domain.EmailVerificationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * dev·local 전용 고정 이메일 인증번호 발급 래퍼(이슈 #180). 심사 계정이 등록된 이메일을 인증할 때만 고정 인증번호를 반환하고 <b>메일 발송을 생략</b>하며,
 * 그 외 모든 요청은 실제 발급기({@link EmailVerificationCodeIssuerImpl})에 위임한다 — 일반 사용자의 이메일 인증은 그대로 동작한다.
 *
 * <p>가로채는 지점이 발송기가 아니라 <b>발급기</b>인 것이 핵심이다. 발송만 건너뛰면 랜덤 인증번호가 그대로 챌린지에 저장되어 심사자가 받은 고정 번호로는 검증에
 * 실패한다.
 *
 * <p>검증({@code EmailVerificationService#verify})은 전혀 손대지 않는다 — 시도 상한·만료·해시 대조가 평소대로 적용되고, 심사 계정은
 * "인증번호가 예측 가능한 정상 사용자"일 뿐이다.
 *
 * <p><b>심사 계정이 설정에 없는 이메일을 제출하면 실제 발송으로 흘려보내지 않고 거절한다.</b> 심사자는 외부 메일을 수신할 수 없으므로 흘려보내야 얻는 것이 없고,
 * "코드가 오지 않는" 막다른 길이 되어 원인 파악만 어려워진다. 이때 {@link EmailVerificationFailedException}(422)을 재사용하는데 — 발송
 * 단계인데 "인증번호가 올바르지 않다"는 메시지가 나가므로 <b>문구는 정확하지 않다</b>. dev 전용 우회 때문에 운영 에러 카탈로그를 늘리지 않는 쪽을 택했고, 진짜
 * 사유는 위 {@code log.warn}으로 남긴다(응답으로 우회 기능의 존재를 노출하지 않는 이점도 있다).
 *
 * <p>이중 게이트({@code @Profile({"local","dev"})} + {@code app.auth.fixed-verification.enabled=true})로만
 * 등록된다. {@code @Primary}로 {@code EmailVerificationService}가 이 래퍼를 주입받고, 래퍼는 실제 구현을 concrete 타입으로
 * 주입해 위임한다(순환 없음, {@link TestLoginOidcTokenVerifier}와 동일 구조).
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
class FixedCodeEmailVerificationCodeIssuer implements EmailVerificationCodeIssuer {

  private final EmailVerificationCodeIssuerImpl delegate;
  private final FixedVerificationPolicy policy;

  @Override
  public String issue(long userId, String email) {
    if (policy.appliesToEmail(userId, email)) {
      log.warn("[FIXED VERIFICATION] 심사 계정 이메일 인증 — 고정 인증번호 발급, 메일 미발송 (userId={}).", userId);
      return policy.code();
    }
    if (policy.isReviewAccount(userId)) {
      // 심사자는 외부 메일을 수신할 수 없다 — 흘려보내면 "코드가 오지 않는" 막다른 길이 되므로 발송 전에 막는다.
      log.warn(
          "[FIXED VERIFICATION] 심사 계정이 설정에 없는 이메일로 인증을 요청했다 — 실제 발송을 막고 거절한다 (userId={}). "
              + "app.auth.fixed-verification.accounts에 등록된 값과 심사 노트가 일치하는지 확인할 것.",
          userId);
      throw new EmailVerificationFailedException();
    }
    return delegate.issue(userId, email);
  }
}
