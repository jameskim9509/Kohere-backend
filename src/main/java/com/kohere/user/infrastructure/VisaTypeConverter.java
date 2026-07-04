package com.kohere.user.infrastructure;

import com.kohere.user.domain.VisaType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link VisaType}을 DB 문자열({@link VisaType#getValue()}, 예 {@code STUDY_D-2})로 영속화하는 JPA 컨버터. 값에
 * 하이픈이 있어 {@code @Enumerated(STRING)}(상수명 저장)을 쓸 수 없다(#93). 저장은 {@code value}, 로드는 {@link
 * VisaType#fromValue(String)}.
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
