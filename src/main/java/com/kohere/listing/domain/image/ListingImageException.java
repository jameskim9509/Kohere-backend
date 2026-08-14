package com.kohere.listing.domain.image;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 요청에 실린 사진이 규칙을 벗어났다.
 *
 * <p>위반 대상이 JSON 필드가 아니라 multipart part라 {@code INVALID_INPUT}의 {@code errors[]}에 담을 필드 경로가 없다.
 * 사용자에게 요구할 행동도 장수·용량·형식마다 달라 코드를 나눈다(ADR-0041, error-response-guide §4).
 */
public class ListingImageException extends BusinessException {

  private ListingImageException(ErrorCode errorCode) {
    super(errorCode);
  }

  /** 지점·방 사진 장수가 허용 범위를 벗어났다. */
  public static ListingImageException countOutOfRange() {
    return new ListingImageException(ErrorCode.LISTING_IMAGE_REQUIRED);
  }

  /** 사진 한 장이 허용 크기를 넘었다. */
  public static ListingImageException tooLarge() {
    return new ListingImageException(ErrorCode.LISTING_IMAGE_TOO_LARGE);
  }

  /** 허용 목록에 없는 이미지 형식이다. */
  public static ListingImageException unsupportedType() {
    return new ListingImageException(ErrorCode.LISTING_IMAGE_UNSUPPORTED_TYPE);
  }
}
