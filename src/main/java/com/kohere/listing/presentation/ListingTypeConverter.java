package com.kohere.listing.presentation;

import com.kohere.listing.domain.ListingType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * 매물 조회 API의 {@code type} 쿼리 파라미터를 내부 {@link ListingType}으로 변환한다.
 *
 * <p>도메인과 MongoDB의 정식 값은 기존 {@code GOSIWON}을 유지하되, 프론트엔드가 임시 호환 값인 {@code GOSHIWON}을 보내는 경우에도 동일한
 * 고시원 유형으로 조회할 수 있도록 요청 경계에서만 별칭을 정규화한다. 따라서 이 변환기는 MongoDB에 저장되는 값이나 API 응답 직렬화에는 영향을 주지 않는다.
 *
 * <p>{@code GOSHIWON} 외의 값은 {@link ListingType#valueOf(String)}에 위임한다. 이로써 {@code GOSIWON}, {@code
 * CO_LIVING}, {@code SHARE_HOUSE}, {@code OTHER}의 기존 동작과 잘못된 값에 대한 기존 오류 처리를 그대로 보존한다. 프론트엔드와 백엔드의
 * 표기가 하나로 통일되면 이 임시 변환기와 관련 호환 테스트를 함께 제거할 수 있다.
 */
@Component
public final class ListingTypeConverter implements Converter<String, ListingType> {

  private static final String GOSHIWON_ALIAS = "GOSHIWON";

  @Override
  public ListingType convert(String source) {
    if (GOSHIWON_ALIAS.equals(source)) {
      return ListingType.GOSIWON;
    }

    return ListingType.valueOf(source);
  }
}
