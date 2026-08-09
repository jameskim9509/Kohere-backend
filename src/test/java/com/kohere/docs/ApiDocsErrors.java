package com.kohere.docs;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.epages.restdocs.apispec.ResourceSnippetParameters;
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;
import org.springframework.restdocs.request.ParameterDescriptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 실패 응답 스니펫과 그 변형(#151).
 *
 * <p>{@code summary}·{@code description}은 <b>성공 스니펫과 같은 상수</b>를 넘겨야 한다 — {@code OpenApi3Generator}는
 * 같은 {@code (path, method)} 모델들의 summary/description 중 <b>첫 non-blank 하나</b>만 채택하고 순서는 파일 순회에 좌우된다.
 * 지금 {@code DELETE /bookings/{bookingId}}는 에러 스니펫 문구가 성공 오퍼레이션의 제목·설명을 차지하고 있다(1단계 실측).
 */
public final class ApiDocsErrors {

  private ApiDocsErrors() {}

  public static RestDocumentationResultHandler errorSnippet(
      String identifier, String tag, String summary, String description, String... errorCodes) {
    return document(
        identifier,
        resource(
            ResourceSnippetParameters.builder()
                .tag(tag)
                .summary(summary)
                .description(description)
                .responseFields(ApiDocsFields.errorFields(errorCodes))
                .build()));
  }

  /**
   * path 변수가 있는 엔드포인트용. 에러 스니펫에도 선언해야 파라미터 설명이 순서에 좌우되지 않는다 — 생성기는 오퍼레이션의 {@code parameters}를 그룹의
   * <b>첫 ResourceModel</b> 하나에서만 가져온다.
   */
  public static RestDocumentationResultHandler errorSnippet(
      String identifier,
      String tag,
      String summary,
      String description,
      ParameterDescriptor[] pathParameters,
      String... errorCodes) {
    return document(
        identifier,
        resource(
            ResourceSnippetParameters.builder()
                .tag(tag)
                .summary(summary)
                .description(description)
                .pathParameters(pathParameters)
                .responseFields(ApiDocsFields.errorFields(errorCodes))
                .build()));
  }

  /**
   * OpenAPI에서만 제외하고 REST Docs HTML({@code /docs})은 유지한다.
   *
   * <p>{@code com.epages} 래퍼 대신 스프링 순정 {@code document()}를 쓰면 {@code resource.json}이 생기지 않아 {@code
   * openapi3} 수집 대상({@code walkTopDown().filter { name == "resource.json" }})에서 빠진다. {@code .adoc}
   * 스니펫은 그대로 생성되므로 {@code index.adoc}이 include하는 스니펫을 내릴 때 쓴다. {@code privateResource(true)}는 이 저장소
   * 설정({@code separatePublicApi} 미사용)에서는 효과가 없다.
   */
  public static void legacyDocError(
      MockMvc mockMvc,
      MockHttpServletRequestBuilder request,
      ResultMatcher expectedStatus,
      String expectedCode,
      String identifier,
      String... errorCodes)
      throws Exception {
    mockMvc
        .perform(request)
        .andExpect(expectedStatus)
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(expectedCode))
        .andDo(
            org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document(
                identifier, responseFields(ApiDocsFields.errorFields(errorCodes))));
  }

  /**
   * 문서화 없이 계약만 지킨다(스니펫 완전 제거 대상). 회귀 방어는 유지하고 Swagger 노이즈만 없앤다.
   *
   * <p>8단계에서 오퍼레이션당 401은 {@code -token-expired} 1건, 400은 {@code -invalid-input} 1건만 남기는데, 제거 대상이 전부
   * 파일별 공용 {@code perform(...)} 헬퍼를 통과하므로 호출부를 인라인으로 풀지 않고 인자 교체 수준으로 끝내기 위한 변형이다.
   */
  public static void assertError(
      MockMvc mockMvc,
      MockHttpServletRequestBuilder request,
      ResultMatcher expectedStatus,
      String expectedCode)
      throws Exception {
    mockMvc
        .perform(request)
        .andExpect(expectedStatus)
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(expectedCode));
  }
}
