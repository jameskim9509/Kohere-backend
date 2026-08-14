package com.kohere.listing.domain.image;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 등록 요청이 가리킨 사진 키를 쓸 수 없다.
 *
 * <p>세 경우를 <b>한 코드로 묶는다</b> — 남의 키, 존재하지 않는 키, 7일이 지나 만료된 키. 구분해 알려주면 남의 키가 있는지 없는지가 새어 나가고, 클라이언트가
 * 할 일은 셋 다 같다(사진을 다시 올린다).
 *
 * <p>저장소 장애({@link ListingImageUploadException}, 502)와 갈라 두는 것이 중요하다. 이 구분이 없으면 사용자가 잘못된 키를 보낸 것이
 * "서버 장애니 재시도하라"는 502로 나가고, 재시도해도 영원히 실패한다(ADR-0041 §4).
 */
public class ListingImageKeyNotFoundException extends BusinessException {

  public ListingImageKeyNotFoundException() {
    super(ErrorCode.LISTING_IMAGE_KEY_NOT_FOUND);
  }

  /**
   * 저장소가 알려 준 부재 원인을 보존한다.
   *
   * @param cause 원본을 찾지 못한 실제 원인. 클라이언트 응답에는 노출되지 않는다
   */
  public ListingImageKeyNotFoundException(Throwable cause) {
    super(ErrorCode.LISTING_IMAGE_KEY_NOT_FOUND);
    initCause(cause);
  }
}
