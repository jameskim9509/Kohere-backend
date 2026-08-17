package com.kohere.auth.application;

import com.kohere.auth.domain.AppleAuthClient;
import com.kohere.auth.domain.LocalAccountRepository;
import com.kohere.auth.domain.Provider;
import com.kohere.auth.domain.RefreshTokenRepository;
import com.kohere.auth.domain.SocialAccount;
import com.kohere.auth.domain.SocialAccountRepository;
import com.kohere.user.api.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 회원 탈퇴 이벤트 구독(ADR-0002/0014). user가 발행한 {@link UserWithdrawnEvent}를 받아 auth 소관 정리를 수행한다 — Apple 연동
 * 폐기, <b>자격증명 매핑 삭제(social_accounts·local_accounts 양쪽)</b>, 해당 user의 refresh 토큰 일괄 무효화.
 *
 * <p>탈퇴 트랜잭션 내에서 동기 처리한다(운영에서 비동기 분리가 필요하면 {@code @ApplicationModuleListener}로 전환). Apple {@code
 * /auth/revoke}는 매핑 삭제 <b>전에</b> 호출하며(삭제되면 토큰을 못 읽음), <b>best-effort</b>라 어떤 실패도 탈퇴를 막지
 * 않는다(ADR-0031 #5). 로컬 정리(매핑 삭제·refresh 무효화)는 동기·같은 트랜잭션이다.
 *
 * <p><b>로그인 수단은 하나도 남기지 않는다</b> — 탈퇴는 {@code users} 행을 지우지 않고 {@code WITHDRAWN}으로 익명화만 하므로({@code
 * user_type}은 {@code LANDLORD} 그대로다) 자격증명이 하나라도 살아 있으면 탈퇴자가 정식 세션을 다시 받는다. 앱(소셜)이 그렇지 않은 것은 여기서
 * {@code social_accounts}를 지우기 때문이고, 임대인 웹(로컬)도 같은 이유로 {@code local_accounts}를 지운다(ADR-0047이 말하는 두
 * 채널의 대칭). 덤으로 {@code local_accounts.email} UNIQUE가 풀려 <b>같은 이메일로 재가입</b>할 수 있게 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserWithdrawnEventListener {

  private final SocialAccountRepository socialAccountRepository;
  private final LocalAccountRepository localAccountRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final AppleAuthClient appleAuthClient;

  @EventListener
  public void onUserWithdrawn(UserWithdrawnEvent event) {
    revokeAppleLinks(event.userId());
    socialAccountRepository.deleteByUserId(event.userId());
    // 웹 자격증명도 같은 자리에서 지운다 — 두 채널의 정리 시점이 갈리면 "탈퇴하면 로그인 수단이 사라진다"가
    // 채널마다 다른 규칙이 된다. 웹 계정이 없는 세입자·앱 전용 임대인은 0행 삭제라 무해하다.
    localAccountRepository.deleteByUserId(event.userId());
    refreshTokenRepository.revokeAllByUserId(event.userId());
  }

  /**
   * 이 회원의 <b>모든</b> 소셜 매핑을 훑어 Apple 연동을 하나도 남김없이 폐기한다(App Store 5.1.1(v)). 매핑 삭제 <b>전에</b> 호출해야 저장된
   * refresh token을 읽을 수 있다(ADR-0031 #5).
   *
   * <p><b>한 회원에 매핑이 여러 행인 것은 병합(US-1-15) 이후의 정상 상태다</b> — 웹에서 먼저 가입한 임대인이 Google로 앱 로그인해 병합되고 나중에
   * Apple로도 앱 로그인해 다시 병합되면 살아남은 계정에 {@code GOOGLE}·{@code APPLE} 두 행이 붙는다({@code user_id}에는 UNIQUE가
   * 없고 인덱스만 있다). 그래서 단건 조회로 첫 행만 보면 두 가지가 동시에 깨진다: ① 순서에 따라 Apple 폐기가 조용히 누락되고, ② 애초에 Spring Data의
   * 단건 조회가 2행에서 {@code IncorrectResultSizeDataAccessException}을 던져 <b>탈퇴 트랜잭션 전체가 롤백</b>된다(그 예외는
   * {@code DataIntegrityViolationException}이 아니라 전역 핸들러의 마지막 그물까지 흘러 500이다) — 병합을 마친 임대인이 <b>영원히
   * 탈퇴하지 못하는</b> 상태가 된다. 그래서 {@link SocialAccountRepository#findAllByUserId}로 전부 받아 순회한다.
   *
   * <p><b>{@code try/catch}가 매핑 <i>하나마다</i> 있는 것도 그래서다.</b> 반복문 바깥에 두면 첫 폐기 실패가 남은 매핑의 폐기까지 건너뛰고, 더
   * 나쁘게는 예외가 이 메서드 밖으로 새어 나가 탈퇴 자체를 막는다 — 탈퇴는 동기·같은 트랜잭션이라 여기서 던지면 그대로 롤백이다. 폐기는 best-effort이며 어떤
   * 실패도 탈퇴를 막지 않는다(ADR-0014/0031). 멱등(이미 폐기=성공) 처리는 어댑터가 담당한다.
   */
  private void revokeAppleLinks(long userId) {
    for (SocialAccount account : socialAccountRepository.findAllByUserId(userId)) {
      revokeAppleLink(userId, account);
    }
  }

  /** 매핑 한 건의 Apple 폐기. Apple이 아니거나 저장된 토큰이 없으면 건너뛰고, 폐기 실패는 WARN만 남긴다(다음 매핑은 계속 처리된다). */
  private void revokeAppleLink(long userId, SocialAccount account) {
    if (account.getProvider() != Provider.APPLE) {
      return;
    }
    if (!StringUtils.hasText(account.getAppleRefreshToken())) {
      // 마이그레이션 이전/미연동 Apple 사용자 — 폐기할 토큰 없음(다음 로그인 때 백필, ADR-0031 #5)
      log.warn("Apple 토큰 폐기 스킵 — 저장된 refresh token 없음: userId={}", userId);
      return;
    }
    try {
      appleAuthClient.revokeRefreshToken(account.getAppleRefreshToken());
    } catch (RuntimeException e) {
      // best-effort — 폐기 실패가 탈퇴를 롤백/차단하지 않고, 남은 매핑의 폐기도 건너뛰지 않는다(ADR-0014/0031)
      log.warn("Apple 토큰 폐기 실패(best-effort, 탈퇴는 계속): userId={}", userId, e);
    }
  }
}
