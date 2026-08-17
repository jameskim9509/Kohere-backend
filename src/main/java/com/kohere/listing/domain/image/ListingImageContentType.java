package com.kohere.listing.domain.image;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 매물 사진으로 받아들이는 이미지 형식이다.
 *
 * <p>허용 목록을 열거로 두어 두 가지를 한곳에서 결정한다 — 업로드를 거절할지({@code 415})와, 저장 키에 붙일 확장자다. 서버는 형식을 변환하지 않으므로
 * HEIC도 받은 그대로 저장한다.
 */
public enum ListingImageContentType {
  JPEG("image/jpeg", "jpg"),
  PNG("image/png", "png"),
  WEBP("image/webp", "webp"),
  HEIC("image/heic", "heic");

  private final String mediaType;
  private final String extension;

  ListingImageContentType(String mediaType, String extension) {
    this.mediaType = mediaType;
    this.extension = extension;
  }

  /** {@code Content-Type} 헤더에 그대로 실리는 미디어 타입이다. */
  public String mediaType() {
    return mediaType;
  }

  /** 저장 키 끝에 붙일 확장자다. 점(.)은 포함하지 않는다. */
  public String extension() {
    return extension;
  }

  /**
   * 요청이 실어 보낸 {@code Content-Type}을 허용 형식으로 해석한다.
   *
   * <p>{@code image/jpeg; charset=...}처럼 파라미터가 붙어 오거나 대문자로 오는 경우가 있어, 세미콜론 앞부분만 소문자로 비교한다. 허용 목록에
   * 없으면 빈 값이며 호출자가 {@code 415}로 옮긴다.
   */
  public static Optional<ListingImageContentType> from(String contentType) {
    if (contentType == null) {
      return Optional.empty();
    }
    String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    return Arrays.stream(values()).filter(type -> type.mediaType.equals(normalized)).findFirst();
  }

  /** 오류 메시지·문서에 쓸 허용 형식 목록이다. */
  public static String allowedMediaTypes() {
    return String.join(
        ", ", Arrays.stream(values()).map(ListingImageContentType::mediaType).toList());
  }
}
