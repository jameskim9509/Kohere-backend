package com.kohere.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.kohere.listing.application.ListingImageConfirmer.ConfirmedListingImages;
import com.kohere.listing.domain.image.ListingImageKeyNotFoundException;
import com.kohere.listing.domain.image.ListingImageKeySet;
import com.kohere.listing.domain.image.ListingImageStorage;
import com.kohere.listing.domain.image.ListingImageUploadException;
import com.kohere.listing.domain.image.StoredListingImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 확정 복사와 되돌리기를 검증한다.
 *
 * <p>저장소와 DB에 걸친 쓰기라 분산 트랜잭션이 없다 — 되돌리기가 실제로 도는지가 "사진 없는 매물"과 "참조 없는 파일" 중 어느 쪽이 남는지를 가른다. 특히
 * <b>임시본을 남기는지</b>가 중요하다. 실패해도 남겨야 사용자가 같은 키로 다시 제출할 수 있다(ADR-0041 §4).
 */
@ExtendWith(MockitoExtension.class)
class ListingImageConfirmerTest {

  private static final String LISTING_ID = "68e0000000000000000000a1";
  private static final List<String> ROOM_OFFER_IDS =
      List.of("68e0000000000000000001a1", "68e0000000000000000001a2");

  @Mock private ListingImageStorage listingImageStorage;

  private ListingImageConfirmer confirmer;

  @BeforeEach
  void setUp() {
    confirmer = new ListingImageConfirmer(listingImageStorage);
  }

  /** 보낸 순서가 곧 표시 순서다 — 첫 커버가 카드 대표 이미지가 되므로 뒤섞이면 다른 사진이 대표가 된다. */
  @Test
  void confirm_보낸_순서대로_URL을_돌려준다() {
    givenStorageEchoesTargetKey();

    ConfirmedListingImages confirmed =
        confirmer.confirm(LISTING_ID, ROOM_OFFER_IDS, keys(2, List.of(2, 3)));

    assertThat(confirmed.coverUrls()).hasSize(2);
    assertThat(confirmed.coverUrls()).allMatch(url -> url.contains("/cover/"));
    assertThat(confirmed.roomUrls().get(0)).hasSize(2);
    assertThat(confirmed.roomUrls().get(1)).hasSize(3);
    assertThat(confirmed.roomUrls().get(0))
        .allMatch(url -> url.contains("/rooms/" + ROOM_OFFER_IDS.get(0) + "/"));
    assertThat(confirmed.copiedKeys()).hasSize(7);
  }

  /** 확정 키는 임시 키의 파일명을 그대로 옮겨 붙인다 — 확장자가 어긋날 여지가 없다. */
  @Test
  void confirm_임시_파일명을_확정_키에_옮긴다() {
    givenStorageEchoesTargetKey();
    ListingImageKeySet keys =
        new ListingImageKeySet(
            List.of("uploads/42/abc-1.webp"),
            List.of(List.of("uploads/42/def-1.png", "uploads/42/def-2.png")));

    ConfirmedListingImages confirmed = confirmer.confirm(LISTING_ID, List.of("room-1"), keys);

    assertThat(confirmed.copiedKeys().get(0))
        .isEqualTo("listings/" + LISTING_ID + "/cover/abc-1.webp");
    assertThat(confirmed.copiedKeys().get(1))
        .isEqualTo("listings/" + LISTING_ID + "/rooms/room-1/def-1.png");
  }

  /** 복사 도중 실패하면 그 요청이 이미 복사한 것만 지운다 — 다른 매물의 사진을 건드리면 안 된다. */
  @Test
  void confirm_도중_실패하면_이미_복사한것을_지운다() {
    List<String> copied = new ArrayList<>();
    given(listingImageStorage.copy(anyString(), anyString()))
        .willAnswer(
            invocation -> {
              String target = invocation.getArgument(1);
              if (copied.size() == 3) {
                throw new ListingImageUploadException(new IllegalStateException("boom"));
              }
              copied.add(target);
              return new StoredListingImage(target, "https://cdn/" + target);
            });

    assertThatThrownBy(() -> confirmer.confirm(LISTING_ID, ROOM_OFFER_IDS, keys(2, List.of(2, 3))))
        .isInstanceOf(ListingImageUploadException.class);

    ArgumentCaptor<List<String>> deleted = ArgumentCaptor.captor();
    then(listingImageStorage).should().deleteQuietly(deleted.capture());
    assertThat(deleted.getValue()).containsExactlyElementsOf(copied);
  }

  /** 없는 원본은 사용자 오류다 — 저장소 장애와 갈라져 그대로 올라간다. */
  @Test
  void confirm_원본이_없으면_키_오류를_그대로_던진다() {
    given(listingImageStorage.copy(anyString(), anyString()))
        .willThrow(new ListingImageKeyNotFoundException());

    assertThatThrownBy(() -> confirmer.confirm(LISTING_ID, ROOM_OFFER_IDS, keys(1, List.of(2, 2))))
        .isInstanceOf(ListingImageKeyNotFoundException.class);
  }

  /** 매물 저장이 실패했을 때 부르는 되돌리기다. 복사본만 지우고 임시본은 건드리지 않는다. */
  @Test
  void rollback_복사본만_지우고_임시본은_남긴다() {
    givenStorageEchoesTargetKey();
    ListingImageKeySet keys = keys(1, List.of(2, 2));
    ConfirmedListingImages confirmed = confirmer.confirm(LISTING_ID, ROOM_OFFER_IDS, keys);

    confirmer.rollback(confirmed);

    then(listingImageStorage).should().deleteQuietly(confirmed.copiedKeys());
    then(listingImageStorage).should(never()).deleteQuietly(keys.allKeys());
  }

  /** 저장까지 끝난 뒤에만 임시본을 치운다. */
  @Test
  void discardPending_임시본을_지운다() {
    ListingImageKeySet keys = keys(1, List.of(2, 2));

    confirmer.discardPending(keys);

    then(listingImageStorage).should().deleteQuietly(keys.allKeys());
  }

  /** 되돌리기까지 실패해도 원래 실패 원인이 가려지지 않아야 한다 — 가려지면 왜 실패했는지 아무도 모른다. */
  @Test
  void confirm_되돌리기가_실패해도_원래_예외를_던진다() {
    given(listingImageStorage.copy(anyString(), anyString()))
        .willThrow(new ListingImageUploadException(new IllegalStateException("boom")));
    willThrow(new IllegalStateException("delete failed"))
        .given(listingImageStorage)
        .deleteQuietly(any());

    assertThatThrownBy(() -> confirmer.confirm(LISTING_ID, ROOM_OFFER_IDS, keys(1, List.of(2, 2))))
        .isInstanceOf(ListingImageUploadException.class);
  }

  private void givenStorageEchoesTargetKey() {
    given(listingImageStorage.copy(anyString(), anyString()))
        .willAnswer(
            invocation -> {
              String target = invocation.getArgument(1);
              return new StoredListingImage(target, "https://cdn/" + target);
            });
  }

  private static ListingImageKeySet keys(int coverCount, List<Integer> roomCounts) {
    return new ListingImageKeySet(
        pendingKeys("cover", coverCount),
        java.util.stream.IntStream.range(0, roomCounts.size())
            .mapToObj(i -> pendingKeys("room" + i, roomCounts.get(i)))
            .toList());
  }

  private static List<String> pendingKeys(String prefix, int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(i -> "uploads/42/%s-%d.jpg".formatted(prefix, i))
        .toList();
  }
}
