package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.BusinessRegistryVerifier;

/**
 * 크리덴셜·계약 없는 환경(로컬/dev/test)용 {@link BusinessRegistryVerifier} 폴백. 실 비즈노 호출 대신 형식(숫자 10자리)만 맞으면
 * 정상으로 간주해 외부 의존 없이 임대인 가입 흐름이 동작하게 한다. {@code app.bizno.enabled=true}면 {@link
 * BiznoBusinessRegistryVerifier}가 대신 등록된다(ADR-0033). 운영에서 쓰지 않는다.
 */
public class StubBusinessRegistryVerifier implements BusinessRegistryVerifier {

  @Override
  public boolean verify(String businessRegistrationNumber) {
    return businessRegistrationNumber != null
        && businessRegistrationNumber.replaceAll("\\D", "").length() == 10;
  }
}
