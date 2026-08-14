package com.kohere.listing.domain.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 저장 키 형식을 검증한다. 키가 바뀌면 이미 저장된 사진의 URL과 어긋나므로 형식을 못 박아 둔다. */
class ListingImageKeysTest {

  private static final long LANDLORD_ID = 42L;
  private static final String LISTING_ID = "68e0000000000000000000a1";
  private static final String ROOM_OFFER_ID = "68e0000000000000000001a1";

  @Test
  void pending_임대인별_임시_prefix에_둔다() {
    String key = ListingImageKeys.pending(LANDLORD_ID, ListingImageContentType.JPEG);

    assertThat(key).matches("uploads/42/[0-9a-f-]{36}\\.jpg");
  }

  /** 같은 임대인이 여러 장을 올려도 서로 덮어쓰지 않아야 한다. */
  @Test
  void pending_호출마다_다른_파일명을_만든다() {
    String first = ListingImageKeys.pending(LANDLORD_ID, ListingImageContentType.PNG);
    String second = ListingImageKeys.pending(LANDLORD_ID, ListingImageContentType.PNG);

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void isPendingOf_자기_키만_통과시킨다() {
    String mine = ListingImageKeys.pending(LANDLORD_ID, ListingImageContentType.JPEG);

    assertThat(ListingImageKeys.isPendingOf(mine, LANDLORD_ID)).isTrue();
    assertThat(ListingImageKeys.isPendingOf(mine, 99L)).isFalse();
  }

  /** 접두사만 겹치는 다른 임대인(예: 4 vs 42)을 통과시키면 안 된다 — 구분자까지 비교해야 한다. */
  @Test
  void isPendingOf_id가_접두사로_겹쳐도_구분한다() {
    String key = ListingImageKeys.pending(42L, ListingImageContentType.JPEG);

    assertThat(ListingImageKeys.isPendingOf(key, 4L)).isFalse();
  }

  @Test
  void isPendingOf_확정_키나_null은_통과시키지_않는다() {
    String confirmed = ListingImageKeys.confirmedCover(LISTING_ID, "uploads/42/abc-def.jpg");

    assertThat(ListingImageKeys.isPendingOf(confirmed, LANDLORD_ID)).isFalse();
    assertThat(ListingImageKeys.isPendingOf(null, LANDLORD_ID)).isFalse();
  }

  /** 확정 키는 임시 키의 파일명을 그대로 옮겨 붙인다 — 확장자가 어긋날 여지를 없앤다. */
  @Test
  void confirmedCover_파일명을_그대로_옮긴다() {
    String pending = ListingImageKeys.pending(LANDLORD_ID, ListingImageContentType.WEBP);

    String confirmed = ListingImageKeys.confirmedCover(LISTING_ID, pending);

    assertThat(confirmed)
        .isEqualTo(
            "listings/" + LISTING_ID + "/cover/" + pending.substring("uploads/42/".length()));
  }

  @Test
  void confirmedRoom_방별_prefix를_붙인다() {
    String pending = ListingImageKeys.pending(LANDLORD_ID, ListingImageContentType.HEIC);

    String confirmed = ListingImageKeys.confirmedRoom(LISTING_ID, ROOM_OFFER_ID, pending);

    assertThat(confirmed)
        .matches("listings/" + LISTING_ID + "/rooms/" + ROOM_OFFER_ID + "/[0-9a-f-]{36}\\.heic");
  }
}
