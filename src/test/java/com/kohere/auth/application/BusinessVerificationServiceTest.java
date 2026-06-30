package com.kohere.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kohere.auth.domain.BusinessNumberNotVerifiedException;
import com.kohere.auth.domain.BusinessNumberVerificationFailedException;
import com.kohere.auth.domain.BusinessRegistryNumberHasher;
import com.kohere.auth.domain.BusinessRegistryVerifier;
import com.kohere.auth.domain.BusinessVerificationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link BusinessVerificationService} 단위 테스트(Mockito) — 외부 검증(정상 사업자만 마킹·검증 실패 시 미마킹)·온보딩 선행 검사(검증
 * 마커 해시 대조)·온보딩 영속용 해시 산출. 외부 검증·해시·마커 저장 포트를 모킹하고 정책값은 실제 기본값을 쓴다(ADR-0033).
 */
@ExtendWith(MockitoExtension.class)
class BusinessVerificationServiceTest {

  private static final long USER_ID = 9L;
  private static final String BIZ_NUMBER = "1234567890";

  @Mock private BusinessRegistryVerifier verifier;
  @Mock private BusinessRegistryNumberHasher hasher;
  @Mock private BusinessVerificationRepository repository;
  private final BusinessVerificationProperties properties = new BusinessVerificationProperties();

  private BusinessVerificationService service;

  @BeforeEach
  void setUp() {
    service = new BusinessVerificationService(verifier, hasher, repository, properties);
  }

  @Test
  void verify_validBusiness_marksVerifiedWithHash() {
    when(verifier.verify(BIZ_NUMBER)).thenReturn(true);
    when(hasher.hash(BIZ_NUMBER)).thenReturn("biz-hash");

    service.verify(USER_ID, BIZ_NUMBER);

    verify(repository).markVerified(USER_ID, "biz-hash", properties.getVerifiedTtlSeconds());
  }

  @Test
  void verify_invalidBusiness_throwsFailedAndDoesNotMark() {
    when(verifier.verify(BIZ_NUMBER)).thenReturn(false);

    assertThatThrownBy(() -> service.verify(USER_ID, BIZ_NUMBER))
        .isInstanceOf(BusinessNumberVerificationFailedException.class);

    verify(repository, never()).markVerified(anyLong(), any(), anyLong());
  }

  @Test
  void assertVerified_matchingHash_passes() {
    when(repository.findVerifiedHash(USER_ID)).thenReturn(Optional.of("biz-hash"));
    when(hasher.hash(BIZ_NUMBER)).thenReturn("biz-hash");

    service.assertVerified(USER_ID, BIZ_NUMBER);
  }

  @Test
  void assertVerified_noMarker_throwsNotVerified() {
    when(repository.findVerifiedHash(USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.assertVerified(USER_ID, BIZ_NUMBER))
        .isInstanceOf(BusinessNumberNotVerifiedException.class);
  }

  @Test
  void assertVerified_hashMismatch_throwsNotVerified() {
    when(repository.findVerifiedHash(USER_ID)).thenReturn(Optional.of("other-hash"));
    when(hasher.hash(BIZ_NUMBER)).thenReturn("biz-hash");

    assertThatThrownBy(() -> service.assertVerified(USER_ID, BIZ_NUMBER))
        .isInstanceOf(BusinessNumberNotVerifiedException.class);
  }

  @Test
  void hashOf_delegatesToHasher() {
    when(hasher.hash(BIZ_NUMBER)).thenReturn("biz-hash");

    assertThat(service.hashOf(BIZ_NUMBER)).isEqualTo("biz-hash");
  }
}
