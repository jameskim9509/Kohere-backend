package com.kohere.listing.presentation.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 도로명 주소 검색 API의 쿼리 파라미터를 담는다.
 *
 * <p>프론트는 검색어만 전달하고, NCP의 {@code count}, {@code page}, {@code language} 같은 제공자 전용 옵션은 서버 정책으로 고정한다.
 * 검색어의 공백 제거와 길이 검증은 응용 계층에서 일관되게 수행한다.
 */
@Getter
@Setter
public class ListingAddressSearchRequest {

  /** 임대인이 등록 폼 주소 칸에 입력한 원본 검색어. 예: {@code 신촌로 12}. */
  private String keyword;
}
