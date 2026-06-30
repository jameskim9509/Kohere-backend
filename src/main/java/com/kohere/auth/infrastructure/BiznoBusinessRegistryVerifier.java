package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.BusinessRegistryVerifier;
import com.kohere.auth.domain.BusinessVerificationUpstreamException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link BusinessRegistryVerifier} 어댑터 — 비즈노 API(국세청 사업자등록정보 기반)로 진위·상태를 동기 조회한다(ADR-0033). 정상(계속
 * 사업자)이면 {@code true}, 미등록·휴폐업·4xx면 {@code false}(검증 실패 422로 매핑), 5xx/타임아웃/I/O면 {@link
 * BusinessVerificationUpstreamException}(502)을 던진다. 사업자번호는 로깅하지 않는다.
 *
 * <p>TODO(확인 필요): 비즈노 API 엔드포인트·요청/응답 필드 매핑이 미확정이라 아래는 골격이다(ADR-0033 미결). 계약 확정 후 {@code
 * app.bizno.enabled=true}로 활성화한다.
 */
public class BiznoBusinessRegistryVerifier implements BusinessRegistryVerifier {

  private final BiznoProperties properties;
  private final RestClient restClient;

  public BiznoBusinessRegistryVerifier(BiznoProperties properties, RestClient restClient) {
    this.properties = properties;
    this.restClient = restClient;
  }

  @Override
  public boolean verify(String businessRegistrationNumber) {
    String normalized = businessRegistrationNumber.replaceAll("\\D", "");
    try {
      // TODO(ADR-0033): 비즈노 실제 엔드포인트·파라미터·응답 스키마로 교체. 아래는 placeholder.
      String body =
          restClient
              .get()
              .uri(
                  properties.getBaseUrl() + "/status?b_no={no}&key={key}",
                  normalized,
                  properties.getApiKey())
              .retrieve()
              .body(String.class);
      // TODO(ADR-0033): 응답 파싱 — 계속(정상) 사업자 판정. 임시: 본문에 "정상"/"계속" 포함 여부.
      return body != null && (body.contains("정상") || body.contains("계속"));
    } catch (HttpClientErrorException e) {
      return false; // 4xx — 미등록·잘못된 요청 → 검증 실패(서비스가 422로 매핑)
    } catch (RestClientException e) {
      throw new BusinessVerificationUpstreamException(e); // 5xx/timeout/I/O → 502
    }
  }
}
