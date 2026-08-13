package com.kohere.listing.domain.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 저장 키 형식을 검증한다. 키가 바뀌면 이미 저장된 사진의 URL과 어긋나므로 형식을 못 박아 둔다. */
class ListingImageKeysTest {

  private static final String LISTING_ID = "68e0000000000000000000a1";
  private static final String ROOM_OFFER_ID = "68e0000000000000000001a1";

  @Test
  void cover_매물별_프리픽스와_확장자를_붙인다() {
    String key = ListingImageKeys.cover(LISTING_ID, ListingImageContentType.JPEG);

    assertThat(key).matches("listings/" + LISTING_ID + "/cover/[0-9a-f-]{36}\\.jpg");
  }

  @Test
  void room_방별_프리픽스와_확장자를_붙인다() {
    String key = ListingImageKeys.room(LISTING_ID, ROOM_OFFER_ID, ListingImageContentType.WEBP);

    assertThat(key)
        .matches("listings/" + LISTING_ID + "/rooms/" + ROOM_OFFER_ID + "/[0-9a-f-]{36}\\.webp");
  }

  /** 같은 매물에 같은 형식을 여러 장 올려도 서로 덮어쓰지 않아야 한다. */
  @Test
  void cover_호출마다_다른_파일명을_만든다() {
    String first = ListingImageKeys.cover(LISTING_ID, ListingImageContentType.PNG);
    String second = ListingImageKeys.cover(LISTING_ID, ListingImageContentType.PNG);

    assertThat(first).isNotEqualTo(second);
  }
}
