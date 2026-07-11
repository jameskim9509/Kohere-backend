package com.kohere.user.infrastructure;

import com.kohere.user.domain.VisaType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link VisaType}을 DB 문자열({@link VisaType#getValue()}, 예 {@code Short Term Visit(C-1~4, B)})로
 * 영속화하는 JPA 컨버터. DB엔 상수명이 아니라 표시용 라벨(value)을 저장하므로 {@code @Enumerated(STRING)}(상수명 저장) 대신 컨버터를
 * 쓴다(#138). 저장은 {@code value}, 로드는 {@link VisaType#fromValue(String)}. (API 직렬화는 상수명이다.)
 */
@Converter(autoApply = false)
public class VisaTypeConverter implements AttributeConverter<VisaType, String> {

  @Override
  public String convertToDatabaseColumn(VisaType attribute) {
    return attribute == null ? null : attribute.getValue();
  }

  @Override
  public VisaType convertToEntityAttribute(String dbData) {
    return dbData == null ? null : VisaType.fromValue(dbData);
  }
}
