package com.kohere.auth.domain;

import java.util.List;
import java.util.Optional;

/**
 * 소셜 자격 매핑 영속 포트. 구현은 infrastructure에 둔다(의존성 역전). 탈퇴 시 사용자별 매핑 삭제로 재가입을 분리한다(ADR-0014).
 *
 * <p><b>한 회원에 매핑이 여러 행인 것은 정상이다.</b> {@code (provider, provider_user_id)}만 UNIQUE이고 {@code
 * user_id}에는 인덱스만 있다(V1) — 같은 사람이 Google·Apple로 각각 앱 로그인해 차례로 병합되면(US-1-15, {@link
 * #reassignUserId}) 살아남은 계정에 두 행이 붙는다. 그래서 <b>userId로 읽는 조회는 단건이 아니라 {@link List}</b>다({@link
 * #findAllByUserId}). 단건 {@code Optional} 조회를 다시 만들면 Spring Data가 2행에서 {@code
 * IncorrectResultSizeDataAccessException}을 던지는데, 그것은 {@code DataIntegrityViolationException}이 아니라
 * 전역 핸들러의 마지막 그물까지 흘러 <b>500</b>이 된다 — 병합을 마친 임대인이 탈퇴조차 못 하는 형태로 드러난다.
 */
public interface SocialAccountRepository {

  Optional<SocialAccount> findByProviderAndProviderUserId(Provider provider, String providerUserId);

  /**
   * userId에 매달린 매핑을 <b>전부</b> 돌려준다(없으면 빈 리스트). 탈퇴 시 매핑 삭제 전에 각 행의 {@code appleRefreshToken}을 읽어
   * 폐기하고(ADR-0031 #5), 고정 인증번호 정책이 요청 주체의 Google 신원을 되짚는 데 쓴다.
   *
   * <p><b>호출부는 "몇 행인가"를 가정하지 않는다.</b> 병합 이후 N행이 정상이므로, 전부 순회해 조건에 맞는 행을 각각 처리한다 — 첫 행만 보면 Google로
   * 병합한 뒤 Apple로도 들어온 사용자의 Apple 연동이 탈퇴 때 조용히 살아남는다.
   */
  List<SocialAccount> findAllByUserId(long userId);

  SocialAccount save(SocialAccount socialAccount);

  /**
   * 한 회원에 매달린 소셜 자격 매핑을 <b>전부 다른 회원으로 옮긴다</b> — 앱 임대인 온보딩의 웹 계정 병합(US-1-15) 전용이다. 병합에서 실제로 이동하는 것은
   * 이 매핑 하나뿐이며(매물·예약은 {@code user_id}를 공유해 옮길 것이 없다), 이것이 <b>앱 로그인의 열쇠</b>라 이후 소셜 로그인이 대상 계정으로
   * 귀결된다.
   *
   * <p><b>반환이 {@code void}인 것은 의도다.</b> 영향 행 수를 돌려주면 호출부가 "1이어야 한다"고 단언하게 되는데, 그 가정에는 근거가 없다 — 임시
   * 계정은 보통 1행이지만 코드가 그것을 알 이유가 없고, UPDATE는 0행이든 N행이든 안전하다. 반대로 <b>대상 쪽</b>에 매핑이 여러 행이 되는 것도 정상이며(한
   * 사람이 Google·Apple로 각각 앱 로그인해 차례로 병합한 경우) {@code (provider, provider_user_id)} UNIQUE는 값이 달라 위반되지
   * 않는다 — <b>이 메서드가 그 N행 상태를 만드는 장본인</b>이라, userId로 읽는 쪽은 반드시 {@link #findAllByUserId}여야 한다.
   *
   * @param fromUserId 병합에서 사라지는 임시 계정
   * @param toUserId 매핑을 넘겨받을 대상(웹) 계정
   */
  void reassignUserId(long fromUserId, long toUserId);

  void deleteByUserId(Long userId);
}
