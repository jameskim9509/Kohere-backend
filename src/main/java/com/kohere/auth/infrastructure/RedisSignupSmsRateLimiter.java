package com.kohere.auth.infrastructure;

import com.kohere.auth.application.SignupPhoneVerificationProperties;
import com.kohere.auth.domain.PhoneRateLimitException;
import com.kohere.auth.domain.SignupSmsRateLimiter;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 가입용 SMS 레이트리밋 어댑터(Redis). 도메인 포트 {@link SignupSmsRateLimiter}를 구현한다 — 키 {@code
 * signup-phone:rate:phone:{정규화번호}}·{@code signup-phone:rate:ip:{IP}}에 {@code INCR}로 시도를 세고 첫 증가에서만
 * {@code EXPIRE} 1시간을 건다. database-design §4-1 A-5.
 *
 * <p><b>고정 창(fixed window)이다</b> — 창 경계에서 최대 2배(예: 59분에 5회 + 61분에 5회)가 통과할 수 있지만, 방어 목적이 발송비·문자 폭탄
 * 억제라 그 정도 오차는 수용한다. 매 증가마다 TTL을 다시 걸면(슬라이딩) 한도에 걸린 사용자가 재시도를 반복하는 동안 창이 끝없이 밀려 <b>영구 차단</b>이 되므로
 * 쓰지 않는다.
 *
 * <p>Redis만이 카운터의 정본이라 인스턴스를 늘려도 한도가 나뉘지 않는다(로컬 캐시였다면 인스턴스 수만큼 한도가 늘어난다). 카운터는 TTL로 사라지므로 키에 실린
 * 번호·IP도 1시간을 넘겨 남지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RedisSignupSmsRateLimiter implements SignupSmsRateLimiter {

  private static final String PHONE_PREFIX = "signup-phone:rate:phone:";
  private static final String IP_PREFIX = "signup-phone:rate:ip:";
  private static final Duration WINDOW = Duration.ofHours(1);

  private final StringRedisTemplate redis;
  private final SignupPhoneVerificationProperties properties;

  /**
   * 번호·IP 카운터를 <b>둘 다</b> 올린 뒤 판정한다. 번호가 한도를 넘었다고 IP 증가를 건너뛰면, 한 IP가 번호를 바꿔가며 태우는 남용이 각 번호의 한도 뒤에
   * 숨어 IP 카운터에 잡히지 않는다.
   */
  @Override
  public void recordAttempt(String phoneNumber, String clientIp) {
    boolean phoneExceeded =
        incrementAndCheckExceeded(PHONE_PREFIX + phoneNumber, properties.getPhoneMaxPerHour());
    // IP를 판별할 수 없으면(헤더·remote address 모두 부재) 번호 한도만 적용한다 — 정체 불명 호출을 한 버킷에
    // 몰아넣으면 그 하나가 다른 모든 익명 호출자를 막는다.
    boolean ipExceeded =
        StringUtils.hasText(clientIp)
            && incrementAndCheckExceeded(IP_PREFIX + clientIp, properties.getIpMaxPerHour());
    if (phoneExceeded || ipExceeded) {
      throw new PhoneRateLimitException();
    }
  }

  /** 카운터 1 증가 후 한도 초과 여부. 창의 시작은 첫 증가 시점이다. */
  private boolean incrementAndCheckExceeded(String key, int maxPerHour) {
    Long count = redis.opsForValue().increment(key);
    if (count == null) {
      // 파이프라인·트랜잭션 모드에서만 null이며 이 경로엔 해당이 없다. 카운트를 모르는 채로 429를 내면 정상
      // 사용자를 막게 되므로 통과시킨다(한도는 비용 가드이지 인가가 아니다).
      return false;
    }
    // TTL이 없는 키는 영구 차단이 된다 — 첫 증가에서 걸고, 만에 하나 EXPIRE가 유실됐으면 다시 건다(자가 치유).
    if (count == 1L || redis.getExpire(key) < 0) {
      redis.expire(key, WINDOW);
    }
    return count > maxPerHour;
  }
}
