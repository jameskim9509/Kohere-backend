package com.kohere.listing.domain.image;

import java.util.UUID;

/**
 * 매물 사진의 저장 키를 만든다(ADR-0041 §2).
 *
 * <p>키에 매물·방 식별자를 넣어 한 버킷을 여러 도메인이 프리픽스로 나눠 쓴다(생활팁은 {@code life-tips/}). 식별자가 키에 들어가므로 저장소는 매물을
 * 저장하기 전에 식별자를 알아야 한다 — 등록 서비스가 ObjectId를 먼저 발급하는 이유다.
 *
 * <p>파일명은 클라이언트가 보낸 이름을 쓰지 않고 UUID로 새로 만든다. 원본 이름에는 경로 구분자·비ASCII·중복이 섞여 들어올 수 있어 키를 그대로 신뢰할 수 없다.
 */
public final class ListingImageKeys {

  private static final String PREFIX = "listings";

  private ListingImageKeys() {}

  /** 지점 대표사진 키 — {@code listings/{listingId}/cover/{uuid}.{ext}}. */
  public static String cover(String listingId, ListingImageContentType contentType) {
    return "%s/%s/cover/%s.%s"
        .formatted(PREFIX, listingId, UUID.randomUUID(), contentType.extension());
  }

  /** 방 사진 키 — {@code listings/{listingId}/rooms/{roomOfferId}/{uuid}.{ext}}. */
  public static String room(
      String listingId, String roomOfferId, ListingImageContentType contentType) {
    return "%s/%s/rooms/%s/%s.%s"
        .formatted(PREFIX, listingId, roomOfferId, UUID.randomUUID(), contentType.extension());
  }
}
