package com.kohere.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kohere.auth.domain.BusinessVerificationUpstreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * {@link BiznoBusinessRegistryVerifier} 단위 테스트 — RestClient를 {@link MockRestServiceServer}로 감싸 비즈노
 * fapi 응답→판정(조회 번호 일치 + 폐업 아님=true, 미등록/휴폐업/서비스오류=false), HTTP 상태 매핑(4xx→false, 5xx→502), 요청 URL
 * 구성(key·gb·q·type)을 검증한다. 포트를 스텁하는 서비스 테스트가 닿지 못하는 어댑터 실제 파싱 로직이다.
 */
class BiznoBusinessRegistryVerifierTest {

  private static final String BASE_URL = "https://bizno.net/api/fapi";
  private static final String NUMBER = "1234567890"; // 조회 번호(정규화)

  private MockRestServiceServer server;
  private BiznoBusinessRegistryVerifier verifier;

  @BeforeEach
  void setUp() {
    BiznoProperties properties = new BiznoProperties();
    properties.setBaseUrl(BASE_URL);
    properties.setApiKey("test-key");
    RestClient.Builder builder = RestClient.builder();
    BiznoClientConfig.applyBiznoMessageConverters(builder); // 실 구성과 동일한 컨버터(모든 Content-Type 처리)
    server = MockRestServiceServer.bindTo(builder).build();
    verifier = new BiznoBusinessRegistryVerifier(properties, builder.build());
  }

  @Test
  void verify_activeBusiness_returnsTrue_andBuildsRequestUrl() {
    server
        .expect(
            requestTo(
                allOf(
                    containsString("key=test-key"),
                    containsString("gb=1"),
                    containsString("q=1234567890"),
                    containsString("type=json"))))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"resultCode\":0,\"resultMsg\":\"NORMAL SERVICE.\",\"totalCount\":1,"
                    + "\"items\":[{\"company\":\"(주)테스트\",\"bno\":\"123-45-67890\","
                    + "\"bsttcd\":\"01\",\"bstt\":\"계속사업자\",\"EndDt\":\"\"}]}",
                MediaType.APPLICATION_JSON));

    // 하이픈 포함 입력도 정규화해 조회·대조한다.
    assertThat(verifier.verify("123-45-67890")).isTrue();
    server.verify();
  }

  @Test
  void verify_closedBusinessCode03_returnsFalse() {
    // bsttcd=03(폐업자) → 검증 실패
    server
        .expect(requestTo(containsString("q=1234567890")))
        .andRespond(
            withSuccess(
                "{\"resultCode\":0,\"totalCount\":1,\"items\":[{\"bno\":\"123-45-67890\","
                    + "\"bstt\":\"폐업자\",\"bsttcd\":\"03\",\"EndDt\":\"20200101\"}]}",
                MediaType.APPLICATION_JSON));

    assertThat(verifier.verify(NUMBER)).isFalse();
  }

  @Test
  void verify_suspendedBusinessCode02_returnsFalse() {
    // bsttcd=02(휴업자) → 검증 실패
    server
        .expect(requestTo(containsString("q=1234567890")))
        .andRespond(
            withSuccess(
                "{\"resultCode\":0,\"totalCount\":1,\"items\":[{\"bno\":\"123-45-67890\","
                    + "\"bstt\":\"휴업자\",\"bsttcd\":\"02\",\"EndDt\":\"\"}]}",
                MediaType.APPLICATION_JSON));

    assertThat(verifier.verify(NUMBER)).isFalse();
  }

  @Test
  void verify_numberNotInItems_returnsFalse() {
    server
        .expect(requestTo(containsString("q=1234567890")))
        .andRespond(
            withSuccess(
                "{\"resultCode\":0,\"totalCount\":1,\"items\":[{\"bno\":\"999-99-99999\","
                    + "\"bstt\":\"\",\"EndDt\":\"\"}]}",
                MediaType.APPLICATION_JSON));

    assertThat(verifier.verify(NUMBER)).isFalse();
  }

  @Test
  void verify_serviceErrorResultCode_returnsFalse() {
    server
        .expect(requestTo(containsString("q=1234567890")))
        .andRespond(
            withSuccess(
                "{\"resultCode\":-1,\"resultMsg\":\"ERROR\",\"items\":[]}",
                MediaType.APPLICATION_JSON));

    assertThat(verifier.verify(NUMBER)).isFalse();
  }

  @Test
  void verify_clientError_returnsFalse() {
    server
        .expect(requestTo(containsString("q=1234567890")))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThat(verifier.verify(NUMBER)).isFalse();
  }

  @Test
  void verify_serverError_throwsUpstream() {
    server.expect(requestTo(containsString("q=1234567890"))).andRespond(withServerError());

    assertThatThrownBy(() -> verifier.verify(NUMBER))
        .isInstanceOf(BusinessVerificationUpstreamException.class);
  }

  @Test
  void verify_jsonReturnedAsTextHtml_stillParses() {
    // 비즈노가 JSON을 application/json이 아닌 text/html로 반환해도 파싱된다(Content-Type 무관 컨버터).
    server
        .expect(requestTo(containsString("q=1234567890")))
        .andRespond(
            withSuccess(
                "{\"resultCode\":0,\"totalCount\":1,\"items\":[{\"bno\":\"123-45-67890\","
                    + "\"bstt\":\"계속사업자\",\"bsttcd\":\"01\",\"EndDt\":\"\"}]}",
                MediaType.TEXT_HTML));

    assertThat(verifier.verify(NUMBER)).isTrue();
  }

  @Test
  void verify_itemsPaddedWithNull_ignoresNullsAndVerifies() {
    // 비즈노는 items를 고정 슬롯으로 반환해 빈 자리를 null로 패딩한다 — null을 걸러야 NPE가 안 난다.
    server
        .expect(requestTo(containsString("q=1128156766")))
        .andRespond(
            withSuccess(
                "{\"resultCode\":0,\"totalCount\":1,\"items\":[{\"bno\":\"112-81-56766\","
                    + "\"bstt\":\"계속사업자\",\"bsttcd\":\"01\",\"EndDt\":\"\"},null,null,null]}",
                MediaType.APPLICATION_JSON));

    // 하이픈 포함 입력 → q=1128156766(하이픈 제거)로 조회·대조.
    assertThat(verifier.verify("112-81-56766")).isTrue();
    server.verify();
  }

  @Test
  void verify_realClosedBusinessResponse_returnsFalse() {
    // 실제 비즈노 응답 형태(폐업자 + null 패딩 + text/html) — 파싱되고 폐업으로 판정(false).
    server
        .expect(requestTo(containsString("q=1128156766")))
        .andRespond(
            withSuccess(
                "{\"resultCode\":0,\"resultMsg\":\"NORMAL SERVICE.\",\"totalCount\":1,\"items\":["
                    + "{\"company\":\"(주)리노테크\",\"bno\":\"112-81-56766\",\"cno\":\"\","
                    + "\"bsttcd\":\"03\",\"bstt\":\"폐업자\",\"TaxTypeCd\":\"\","
                    + "\"taxtype\":\"부가가치세 일반과세자\",\"EndDt\":\"20250430\"},null,null,null]}",
                MediaType.TEXT_HTML));

    assertThat(verifier.verify("112-81-56766")).isFalse();
  }
}
