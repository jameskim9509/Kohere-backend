package com.kohere.listing.domain.image;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 사진 저장소 업로드 실패다.
 *
 * <p>사용자 입력 문제가 아니라 외부 연동 장애라 전용 매물 코드를 두지 않고 공통 {@code 502 UPSTREAM_ERROR}로 내려간다 — 프론트는 입력 교정이 아니라
 * 재시도를 안내한다(docs/api/error-response-guide.md §4).
 */
public class ListingImageUploadException extends BusinessException {

  /**
   * 원인이 된 SDK·네트워크 예외를 보존해 서버 로그에서 장애 원인을 추적할 수 있게 한다.
   *
   * @param cause 업로드 실패의 실제 원인. 클라이언트 응답에는 노출되지 않는다
   */
  public ListingImageUploadException(Throwable cause) {
    super(ErrorCode.UPSTREAM_ERROR);
    initCause(cause);
  }
}
