package com.kohere.auth.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kohere.auth.domain.AppleAuthClient;
import com.kohere.auth.domain.AppleUpstreamException;
import com.kohere.auth.domain.InvalidSocialTokenException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link AppleAuthClient} 어댑터 — RestClient로 Apple {@code /auth/token}·{@code /auth/revoke}에 form
 * 인코딩 POST를 보낸다(ADR-0031). {@code client_secret}(ES256 JWT)은 {@link AppleClientSecretGenerator}의
 * 인메모리 캐시에서 받는다.
 *
 * <p>오류 매핑: Apple 4xx(잘못/만료/재사용 코드·잘못된 client_secret) → 401 {@link InvalidSocialTokenException},
 * 타임아웃·5xx·I/O → 502 {@link AppleUpstreamException}. revoke는 멱등 — {@code invalid_grant}/{@code
 * invalid_token}(이미 폐기)은 성공으로 본다. 인가코드·refresh token·client_secret은 절대 로깅하지 않는다.
 */
@Component
public class AppleAuthClientImpl implements AppleAuthClient {

  private final AppleProperties properties;
  private final AppleClientSecretGenerator clientSecretGenerator;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public AppleAuthClientImpl(
      AppleProperties properties,
      AppleClientSecretGenerator clientSecretGenerator,
      ObjectMapper objectMapper,
      RestClient appleRestClient) {
    this.properties = properties;
    this.clientSecretGenerator = clientSecretGenerator;
    this.objectMapper = objectMapper;
    this.restClient = appleRestClient;
  }

  @Override
  public AppleTokens exchangeAuthorizationCode(String authorizationCode) {
    MultiValueMap<String, String> form = baseForm();
    form.add("code", authorizationCode);
    form.add("grant_type", "authorization_code");

    String body;
    try {
      body =
          restClient
              .post()
              .uri(properties.getTokenUrl())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(String.class);
    } catch (HttpClientErrorException e) {
      // Apple 4xx — 만료/재사용 코드·client_secret 불일치 등 인증 실패
      throw new InvalidSocialTokenException();
    } catch (RestClientException e) {
      // 타임아웃·5xx·I/O 등 일시 장애
      throw new AppleUpstreamException(e);
    }

    try {
      JsonNode json = objectMapper.readTree(body);
      String idToken = json.path("id_token").asText(null);
      String refreshToken = json.path("refresh_token").asText(null);
      if (!StringUtils.hasText(idToken)) {
        // id_token이 없는 응답은 상류 계약 위반 — 검증 대상이 없으므로 일시 장애로 취급
        throw new AppleUpstreamException(
            new IllegalStateException("Apple token response missing id_token"));
      }
      return new AppleTokens(idToken, refreshToken);
    } catch (JsonProcessingException e) {
      throw new AppleUpstreamException(e);
    }
  }

  @Override
  public void revokeRefreshToken(String refreshToken) {
    MultiValueMap<String, String> form = baseForm();
    form.add("token", refreshToken);
    form.add("token_type_hint", "refresh_token");

    try {
      restClient
          .post()
          .uri(properties.getRevokeUrl())
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form)
          .retrieve()
          .toBodilessEntity();
    } catch (HttpClientErrorException e) {
      if (isAlreadyRevoked(e)) {
        return; // 멱등 — 이미 폐기된 토큰은 성공으로 간주(ADR-0031 #5)
      }
      throw new AppleUpstreamException(e);
    } catch (RestClientException e) {
      throw new AppleUpstreamException(e);
    }
  }

  private MultiValueMap<String, String> baseForm() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", properties.getClientId());
    form.add("client_secret", clientSecretGenerator.generate());
    return form;
  }

  private boolean isAlreadyRevoked(HttpClientErrorException e) {
    try {
      String error = objectMapper.readTree(e.getResponseBodyAsString()).path("error").asText("");
      return "invalid_grant".equals(error) || "invalid_token".equals(error);
    } catch (JsonProcessingException ex) {
      return false;
    }
  }
}
