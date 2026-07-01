package com.kohere.auth.application;

import com.kohere.auth.domain.BusinessNumberVerificationFailedException;
import com.kohere.auth.domain.BusinessRegistryVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 임대인 사업자등록번호 검증 유스케이스. 외부 검증 서비스(비즈노)로 동기 검증해 정상(계속) 사업자 여부만 판정하는 <b>무상태</b> 검증이다 — 온보딩과 분리되어 검증
 * 결과를 서버에 영속하지 않고 응답으로만 돌려준다. 미등록·휴폐업·진위실패는 422, 외부 장애·타임아웃은 어댑터가 502를 던진다(ADR-0033, 시퀀스 US-1-8).
 * 코드 챌린지가 없는 동기 검증이라 연락처/이메일 인증보다 얇다.
 */
@Service
@RequiredArgsConstructor
public class BusinessVerificationService {

  private final BusinessRegistryVerifier verifier;

  /**
   * 사업자번호 동기 검증. 정상(계속) 사업자가 아니면(미등록·휴폐업·진위 실패) 422 {@code
   * AUTH_BUSINESS_NUMBER_VERIFICATION_FAILED}, 외부 장애·타임아웃은 어댑터가 502를 던진다. 검증 결과는 영속하지 않는다(무상태).
   */
  public void verify(String businessRegistrationNumber) {
    if (!verifier.verify(businessRegistrationNumber)) {
      throw new BusinessNumberVerificationFailedException();
    }
  }
}
