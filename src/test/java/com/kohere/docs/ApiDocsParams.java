package com.kohere.docs;

import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.snippet.Attributes.key;

import java.util.List;
import org.springframework.restdocs.request.ParameterDescriptor;

/**
 * path·query 파라미터 기술자(#151). 파라미터에 허용값 목록을 실을 때 쓴다.
 *
 * <p><b>반환형이 {@link ParameterDescriptor}인 이유</b> — {@code com.epages}의 {@code
 * ParameterDescriptorWithType}은 {@code IgnorableDescriptor}를 상속할 뿐 {@link ParameterDescriptor}의 하위
 * 타입이 <b>아니라</b> {@code RequestDocumentation.pathParameters/queryParameters}에 넘길 수 없다. 래퍼가 {@code
 * ParameterDescriptorWithType.fromParameterDescriptor}로 변환하며 attributes를 그대로 복사하므로, {@code
 * OpenApi3Generator.simpleTypeToSchema}가 {@code enumValues}를 읽어 {@code StringSchema.addEnumItem}한다.
 *
 * <p>같은 이유로 {@link ApiDocsFields#codeField}는 여기 쓸 수 없다 — 그건 {@code FieldDescriptor}다.
 */
public final class ApiDocsParams {

  private ApiDocsParams() {}

  /**
   * 허용값이 정해진 path·query 파라미터.
   *
   * <p><b>문자열 리스트만 넘긴다.</b> 생성기는 {@code enumValues} 원소를 {@code SimpleType}에 맞춰 캐스팅하는데 {@code
   * fromParameterDescriptor}가 type을 복사하지 않아 항상 {@code STRING}이다. 정수 리스트를 넘기면 {@code openapi3} 태스크가
   * {@code ClassCastException}으로 죽는다.
   */
  public static ParameterDescriptor codeParam(
      String name, List<String> allowedValues, String description) {
    return parameterWithName(name)
        .attributes(key("enumValues").value(allowedValues))
        .description(description);
  }

  public static ParameterDescriptor optCodeParam(
      String name, List<String> allowedValues, String description) {
    return codeParam(name, allowedValues, description).optional();
  }
}
