package com.kohere.auth.application;

import com.kohere.auth.domain.BusinessNumberNotVerifiedException;
import com.kohere.auth.domain.BusinessNumberVerificationFailedException;
import com.kohere.auth.domain.BusinessRegistryNumberHasher;
import com.kohere.auth.domain.BusinessRegistryVerifier;
import com.kohere.auth.domain.BusinessVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 임대인 사업자등록번호 검증 유스케이스. 외부 검증 서비스(비즈노)로 동기 검증해 정상(계속) 사업자만 해시 마커로 남기고(미등록·휴폐업·진위실패 422, 외부 장애 502),
 * 임대인 온보딩 제출 시 제출 번호 해시를 마커와 대조한다(ADR-0033, 시퀀스 US-1-8). 코드 챌린지가 없는 동기 검증이라 연락처/이메일 인증보다 얇다.
 */
@Service
@RequiredArgsConstructor
public class BusinessVerificationService {

  private final BusinessRegistryVerifier verifier;
  private final BusinessRegistryNumberHasher hasher;
  private final BusinessVerificationRepository repository;
  private final BusinessVerificationProperties properties;

  /**
   * 사업자번호 동기 검증. 정상(계속) 사업자면 해시를 검증 마커로 저장한다. 미등록·휴폐업·진위 실패는 422 {@code
   * AUTH_BUSINESS_NUMBER_VERIFICATION_FAILED}, 외부 장애·타임아웃은 어댑터가 502를 던진다.
   */
  public void verify(long userId, String businessRegistrationNumber) {
    if (!verifier.verify(businessRegistrationNumber)) {
      throw new BusinessNumberVerificationFailedException();
    }
    repository.markVerified(
        userId, hasher.hash(businessRegistrationNumber), properties.getVerifiedTtlSeconds());
  }

  /** 임대인 온보딩 제출 선행 검사 — 제출 사업자번호 해시가 검증 마커와 일치해야 한다(미검증·불일치 422). */
  public void assertVerified(long userId, String businessRegistrationNumber) {
    String verifiedHash =
        repository.findVerifiedHash(userId).orElseThrow(BusinessNumberNotVerifiedException::new);
    if (!verifiedHash.equals(hasher.hash(businessRegistrationNumber))) {
      throw new BusinessNumberNotVerifiedException();
    }
  }

  /** 온보딩 완료 시 user에 영속할 사업자번호 해시(원문 비저장). */
  public String hashOf(String businessRegistrationNumber) {
    return hasher.hash(businessRegistrationNumber);
  }
}
