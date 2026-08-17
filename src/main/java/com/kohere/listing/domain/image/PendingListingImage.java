package com.kohere.listing.domain.image;

import java.io.IOException;
import java.io.InputStream;

/**
 * 아직 저장소에 올리지 않은 사진 한 장이다.
 *
 * <p>바이트가 아니라 여는 방법을 들고 있다. 한 요청이 수십 장을 실어 보낼 수 있어 전부 메모리에 펼치면 요청 하나가 힙을 크게 차지한다.
 *
 * @param contentType 허용 목록을 통과한 이미지 형식
 * @param size 바이트 크기
 * @param content 내용을 여는 방법. 업로드 시점에 한 번 연다
 */
public record PendingListingImage(
    ListingImageContentType contentType, long size, ContentSource content) {

  /** 사진 한 장이 넘을 수 없는 크기다(10MB). */
  public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

  /** 사진 내용을 여는 방법이다. 실제 소스(업로드 파일·파일 시스템·메모리)를 도메인이 알지 않게 감싼다. */
  @FunctionalInterface
  public interface ContentSource {
    InputStream open() throws IOException;
  }

  /**
   * 형식과 크기를 확인해 업로드 대기 사진을 만든다.
   *
   * <p>업로드보다 앞서 검증하는 이유는 거절된 요청이 저장소에 흔적을 남기지 않게 하기 위해서다 — 한 장이라도 규칙을 어기면 아무것도 올리지 않는다.
   *
   * @param rawContentType 요청이 실어 보낸 {@code Content-Type}
   * @param size 바이트 크기
   * @param content 내용을 여는 방법
   * @throws ListingImageException 허용하지 않는 형식이거나, 비었거나, 크기 상한을 넘은 경우
   */
  public static PendingListingImage of(String rawContentType, long size, ContentSource content) {
    ListingImageContentType contentType =
        ListingImageContentType.from(rawContentType)
            .orElseThrow(ListingImageException::unsupportedType);
    if (size <= 0) {
      // 빈 파일은 사진을 보내지 않은 것과 같다. 장수 규칙 위반으로 다뤄야 "몇 장 더 올리라"는 안내가 맞다.
      throw ListingImageException.countOutOfRange();
    }
    if (size > MAX_SIZE_BYTES) {
      throw ListingImageException.tooLarge();
    }
    return new PendingListingImage(contentType, size, content);
  }
}
