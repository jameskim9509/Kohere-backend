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

  /** 확정된 매물 사진이 놓이는 prefix. */
  private static final String LISTING_PREFIX = "listings";

  /** 아직 어느 매물의 것도 아닌 사진이 놓이는 prefix. 만료 규칙이 이 prefix만 대상으로 한다. */
  private static final String PENDING_PREFIX = "uploads";

  private ListingImageKeys() {}

  /**
   * 등록 전 임시 위치의 키를 만든다 — {@code uploads/{landlordId}/{uuid}.{ext}}.
   *
   * <p>업로드 시점에는 매물이 없어 {@code listingId}를 모르므로 임대인 아래에 둔다. 그 {@code landlordId}가 곧 소유권 표시다 — {@link
   * #isPendingOf}가 문자열 비교만으로 남의 사진을 걸러낸다.
   */
  public static String pending(long landlordId, ListingImageContentType contentType) {
    return "%s/%d/%s.%s"
        .formatted(PENDING_PREFIX, landlordId, UUID.randomUUID(), contentType.extension());
  }

  /**
   * 그 임시 키가 이 임대인의 것인지 본다.
   *
   * <p>등록 요청이 남의 {@code uploads/{다른 id}/…}를 가리키면 남이 올린 사진을 자기 매물에 붙일 수 있다. 저장소를 부르기 전에 막는다.
   */
  public static boolean isPendingOf(String key, long landlordId) {
    return key != null && key.startsWith("%s/%d/".formatted(PENDING_PREFIX, landlordId));
  }

  /**
   * 임시 키의 파일명을 그대로 확정 키에 옮겨 붙인다.
   *
   * <p>확장자를 다시 만들지 않으므로 업로드 때 검증한 형식과 확정 키의 확장자가 어긋날 수 없다. 복사가 {@code Content-Type}도 함께 보존하므로 저장소에
   * 물어볼 필요가 없다(ADR-0041 §4).
   */
  private static String fileNameOf(String pendingKey) {
    return pendingKey.substring(pendingKey.lastIndexOf('/') + 1);
  }

  /** 지점 대표사진의 확정 키 — {@code listings/{listingId}/cover/{uuid}.{ext}}. */
  public static String confirmedCover(String listingId, String pendingKey) {
    return "%s/%s/cover/%s".formatted(LISTING_PREFIX, listingId, fileNameOf(pendingKey));
  }

  /** 방 사진의 확정 키 — {@code listings/{listingId}/rooms/{roomOfferId}/{uuid}.{ext}}. */
  public static String confirmedRoom(String listingId, String roomOfferId, String pendingKey) {
    return "%s/%s/rooms/%s/%s"
        .formatted(LISTING_PREFIX, listingId, roomOfferId, fileNameOf(pendingKey));
  }
}
