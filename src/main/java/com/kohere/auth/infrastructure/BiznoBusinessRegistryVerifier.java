package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.BusinessRegistryVerifier;
import com.kohere.auth.domain.BusinessVerificationUpstreamException;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link BusinessRegistryVerifier} 어댑터 — 비즈노 fapi(국세청 사업자등록정보 기반)로 진위·상태를 동기 조회한다(ADR-0033). 요청은
 * {@code GET https://bizno.net/api/fapi?key={apiKey}&gb=1&q={사업자번호}&type=json}(RestClient)이며, 응답
 * {@code items[]}에서 조회 번호와 {@code bno}가 일치하고 폐업({@code EndDt}/상태)이 아닌 사업자가 있으면 정상(계속)으로 본다 → {@code
 * true}. 미등록·휴폐업·4xx면 {@code false}(검증 실패 422로 매핑), 5xx/타임아웃/I/O면 {@link
 * BusinessVerificationUpstreamException}(502)을 던진다. 사업자번호·API 키는 로깅하지 않는다.
 *
 * <p>크리덴셜이 확정되면 {@link BiznoClientConfig}에서 {@code app.bizno.enabled=true}로 활성화한다.
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
    String normalized = digits(businessRegistrationNumber);
    try {
      BiznoApiResponse response =
          restClient
              .get()
              .uri(
                  properties.getBaseUrl() + "?key={key}&gb=1&q={q}&type=json",
                  properties.getApiKey(),
                  normalized)
              .retrieve()
              .body(BiznoApiResponse.class);
      return isVerified(response, normalized);
    } catch (HttpClientErrorException e) {
      return false; // 4xx — 잘못된 요청·미등록 등 → 검증 실패(서비스가 422로 매핑)
    } catch (RestClientException e) {
      throw new BusinessVerificationUpstreamException(e); // 5xx/타임아웃/I-O → 502
    }
  }

  /** 정상 서비스 응답에 조회 번호와 일치하고 폐업이 아닌 사업자가 있으면 검증 성공. */
  private static boolean isVerified(BiznoApiResponse response, String normalized) {
    if (response == null || response.resultCode() != 0 || response.items() == null) {
      return false;
    }
    return response.items().stream()
        .filter(item -> normalized.equals(digits(item.bno())))
        .anyMatch(BiznoBusinessRegistryVerifier::isActive);
  }

  /** 폐업일(EndDt)이 있거나 상태가 휴·폐업이면 비정상. 그 외(상태 비표기 포함)는 계속(정상) 사업자로 본다. */
  private static boolean isActive(BiznoApiResponse.Item item) {
    if (StringUtils.hasText(item.endDt())) {
      return false;
    }
    String status = item.bstt() == null ? "" : item.bstt();
    return !status.contains("폐업") && !status.contains("휴업");
  }

  private static String digits(String value) {
    return value == null ? "" : value.replaceAll("\\D", "");
  }
}
