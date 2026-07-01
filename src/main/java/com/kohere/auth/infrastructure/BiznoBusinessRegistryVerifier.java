package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.BusinessRegistryVerifier;
import com.kohere.auth.domain.BusinessVerificationUpstreamException;
import java.util.Objects;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link BusinessRegistryVerifier} 어댑터 — 비즈노 fapi(국세청 사업자등록정보 기반)로 진위·상태를 동기 조회한다(ADR-0033). 요청은
 * {@code GET https://bizno.net/api/fapi?key={apiKey}&gb=1&q={사업자번호}&type=json}(RestClient)이며, 응답
 * {@code items[]}에서 조회 번호와 {@code bno}가 일치하고 상태 코드({@code bsttcd})가 {@code 01}(계속사업자)인 사업자가 있으면
 * 정상으로 본다 → {@code true}. 미등록·휴폐업·4xx면 {@code false}(검증 실패 422로 매핑), 5xx/타임아웃/I/O면 {@link
 * BusinessVerificationUpstreamException}(502)을 던진다. 사업자번호·API 키는 로깅하지 않는다.
 *
 * <p>크리덴셜이 확정되면 {@link BiznoClientConfig}에서 {@code app.bizno.enabled=true}로 활성화한다.
 */
public class BiznoBusinessRegistryVerifier implements BusinessRegistryVerifier {

  /** 사업자 상태 코드(bsttcd) — 01=계속사업자. 02=휴업자·03=폐업자는 검증 실패로 본다. */
  private static final String STATUS_CODE_CONTINUING = "01";

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

  /**
   * 정상 서비스 응답에 조회 번호와 일치하고 상태 코드(bsttcd)가 계속(01)인 사업자가 있으면 검증 성공. 비즈노는 {@code items}를 고정 슬롯으로 반환해 빈
   * 자리를 {@code null}로 채우므로 먼저 null을 걸러낸다. {@code bno}는 하이픈 포함이라 양쪽을 숫자만 정규화해 대조한다.
   */
  private static boolean isVerified(BiznoApiResponse response, String normalized) {
    if (response == null || response.resultCode() != 0 || response.items() == null) {
      return false;
    }
    return response.items().stream()
        .filter(Objects::nonNull)
        .filter(item -> normalized.equals(digits(item.bno())))
        .anyMatch(BiznoBusinessRegistryVerifier::isActive);
  }

  /**
   * 사업자 상태 코드({@code bsttcd})로 판정한다 — {@code 01}=계속사업자, {@code 02}=휴업자, {@code 03}=폐업자. 계속(01)만
   * 정상(검증 통과)으로 보고 그 외(휴·폐업·미표기)는 보수적으로 미검증한다. 텍스트 상태({@code bstt})·폐업일({@code EndDt})은 표시·로그용
   * 참고값이다.
   */
  private static boolean isActive(BiznoApiResponse.Item item) {
    return STATUS_CODE_CONTINUING.equals(item.bsttcd());
  }

  private static String digits(String value) {
    return value == null ? "" : value.replaceAll("\\D", "");
  }
}
