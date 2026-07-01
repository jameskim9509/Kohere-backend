package com.kohere.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.kohere.auth.domain.BusinessNumberVerificationFailedException;
import com.kohere.auth.domain.BusinessRegistryVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link BusinessVerificationService} 단위 테스트(Mockito) — 무상태 외부 검증만 담당한다. 정상(계속) 사업자면 통과하고,
 * 미등록·휴폐업·진위 실패면 422를 던진다. 검증 결과를 서버에 영속하지 않으므로 마커·해시 저장 로직은 없다(ADR-0033).
 */
@ExtendWith(MockitoExtension.class)
class BusinessVerificationServiceTest {

  private static final String BIZ_NUMBER = "1234567890";

  @Mock private BusinessRegistryVerifier verifier;

  private BusinessVerificationService service;

  @BeforeEach
  void setUp() {
    service = new BusinessVerificationService(verifier);
  }

  @Test
  void verify_validBusiness_passes() {
    when(verifier.verify(BIZ_NUMBER)).thenReturn(true);

    assertThatCode(() -> service.verify(BIZ_NUMBER)).doesNotThrowAnyException();
  }

  @Test
  void verify_invalidBusiness_throwsFailed() {
    when(verifier.verify(BIZ_NUMBER)).thenReturn(false);

    assertThatThrownBy(() -> service.verify(BIZ_NUMBER))
        .isInstanceOf(BusinessNumberVerificationFailedException.class);
  }
}
