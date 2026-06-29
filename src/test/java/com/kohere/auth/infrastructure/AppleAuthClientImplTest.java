package com.kohere.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kohere.auth.domain.AppleAuthClient;
import com.kohere.auth.domain.AppleUpstreamException;
import com.kohere.auth.domain.InvalidSocialTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * {@link AppleAuthClientImpl} 단위 테스트 — RestClient를 {@link MockRestServiceServer}로 감싸 Apple HTTP
 * 상태→예외 매핑(4xx→401, 5xx/IO→502), 폼 파라미터 구성(redirect_uri 미전송), revoke
 * 멱등성(invalid_grant/invalid_token=성공)을 검증한다. 포트를 스텁하는 통합 테스트가 닿지 못하는 어댑터의 실제 변환 로직이다.
 */
class AppleAuthClientImplTest {

  private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";
  private static final String REVOKE_URL = "https://appleid.apple.com/auth/revoke";

  private MockRestServiceServer server;
  private AppleAuthClientImpl client;

  @BeforeEach
  void setUp() {
    AppleProperties properties = new AppleProperties();
    properties.setClientId("com.kohere.app");
    properties.setTokenUrl(TOKEN_URL);
    properties.setRevokeUrl(REVOKE_URL);
    AppleClientSecretGenerator generator = mock(AppleClientSecretGenerator.class);
    when(generator.generate()).thenReturn("client-secret-jwt");

    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new AppleAuthClientImpl(properties, generator, new ObjectMapper(), builder.build());
  }

  @Test
  void exchange_success_returnsTokensAndOmitsRedirectUri() {
    server
        .expect(requestTo(TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(content().string(containsString("grant_type=authorization_code")))
        .andExpect(content().string(containsString("client_id=com.kohere.app")))
        .andExpect(content().string(containsString("client_secret=client-secret-jwt")))
        .andExpect(content().string(containsString("code=auth-code")))
        .andExpect(content().string(not(containsString("redirect_uri")))) // 네이티브 iOS
        .andRespond(
            withSuccess(
                "{\"id_token\":\"idtok\",\"refresh_token\":\"rt\"}", MediaType.APPLICATION_JSON));

    AppleAuthClient.AppleTokens tokens = client.exchangeAuthorizationCode("auth-code");

    assertThat(tokens.idToken()).isEqualTo("idtok");
    assertThat(tokens.refreshToken()).isEqualTo("rt");
    server.verify();
  }

  @Test
  void exchange_appleClientError_throwsInvalidSocialToken() {
    server
        .expect(requestTo(TOKEN_URL))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"error\":\"invalid_grant\"}")
                .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.exchangeAuthorizationCode("auth-code"))
        .isInstanceOf(InvalidSocialTokenException.class);
  }

  @Test
  void exchange_appleServerError_throwsUpstream() {
    server.expect(requestTo(TOKEN_URL)).andRespond(withServerError());

    assertThatThrownBy(() -> client.exchangeAuthorizationCode("auth-code"))
        .isInstanceOf(AppleUpstreamException.class);
  }

  @Test
  void exchange_successButMissingIdToken_throwsUpstream() {
    server
        .expect(requestTo(TOKEN_URL))
        .andRespond(withSuccess("{\"refresh_token\":\"rt\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.exchangeAuthorizationCode("auth-code"))
        .isInstanceOf(AppleUpstreamException.class);
  }

  @Test
  void revoke_success_sendsRefreshTokenParams() {
    server
        .expect(requestTo(REVOKE_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("token=rt")))
        .andExpect(content().string(containsString("token_type_hint=refresh_token")))
        .andRespond(withSuccess());

    assertThatCode(() -> client.revokeRefreshToken("rt")).doesNotThrowAnyException();
    server.verify();
  }

  @Test
  void revoke_alreadyRevoked_isIdempotentSuccess() {
    server
        .expect(requestTo(REVOKE_URL))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"error\":\"invalid_token\"}")
                .contentType(MediaType.APPLICATION_JSON));

    assertThatCode(() -> client.revokeRefreshToken("rt")).doesNotThrowAnyException();
  }

  @Test
  void revoke_otherClientError_throwsUpstream() {
    server
        .expect(requestTo(REVOKE_URL))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"error\":\"invalid_request\"}")
                .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.revokeRefreshToken("rt"))
        .isInstanceOf(AppleUpstreamException.class);
  }

  @Test
  void revoke_serverError_throwsUpstream() {
    server.expect(requestTo(REVOKE_URL)).andRespond(withServerError());

    assertThatThrownBy(() -> client.revokeRefreshToken("rt"))
        .isInstanceOf(AppleUpstreamException.class);
  }
}
