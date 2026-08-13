package com.kohere.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.kohere.listing.application.ListingImageUploader.UploadedListingImages;
import com.kohere.listing.domain.image.ListingImageContentType;
import com.kohere.listing.domain.image.ListingImageParts;
import com.kohere.listing.domain.image.ListingImageStorage;
import com.kohere.listing.domain.image.ListingImageUpload;
import com.kohere.listing.domain.image.ListingImageUploadException;
import com.kohere.listing.domain.image.PendingListingImage;
import com.kohere.listing.domain.image.StoredListingImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 업로드 순서와 실패 시 되돌리기를 검증한다.
 *
 * <p>저장소와 DB에 걸친 쓰기라 분산 트랜잭션이 없다 — 되돌리기가 실제로 도는지가 "사진 없는 매물"과 "참조 없는 파일" 중 어느 쪽이 남는지를 가른다(ADR-0041
 * §3).
 */
@ExtendWith(MockitoExtension.class)
class ListingImageUploaderTest {

  private static final String LISTING_ID = "68e0000000000000000000a1";
  private static final List<String> ROOM_OFFER_IDS =
      List.of("68e0000000000000000001a1", "68e0000000000000000001a2");

  @Mock private ListingImageStorage listingImageStorage;

  private ListingImageUploader uploader;

  @BeforeEach
  void setUp() {
    uploader = new ListingImageUploader(listingImageStorage);
  }

  /** 요청 순서가 곧 응답 URL 순서다 — 첫 지점 사진이 카드 대표 이미지가 되므로 뒤섞이면 다른 사진이 대표가 된다. */
  @Test
  void upload_요청_순서대로_URL을_돌려준다() {
    givenStorageEchoesKey();

    UploadedListingImages uploaded =
        uploader.upload(LISTING_ID, ROOM_OFFER_IDS, parts(2, List.of(2, 3)));

    assertThat(uploaded.coverUrls()).hasSize(2);
    assertThat(uploaded.coverUrls()).allMatch(url -> url.contains("/cover/"));
    assertThat(uploaded.roomUrls().get(0)).hasSize(2);
    assertThat(uploaded.roomUrls().get(1)).hasSize(3);
    assertThat(uploaded.roomUrls().get(0))
        .allMatch(url -> url.contains("/rooms/" + ROOM_OFFER_IDS.get(0) + "/"));
    assertThat(uploaded.roomUrls().get(1))
        .allMatch(url -> url.contains("/rooms/" + ROOM_OFFER_IDS.get(1) + "/"));
    assertThat(uploaded.keys()).hasSize(7);
  }

  /** 업로드 도중 실패하면 그 요청이 이미 올린 것만 지운다 — 다른 매물의 사진을 건드리면 안 된다. */
  @Test
  void upload_도중_실패하면_이미_올린것을_지운다() {
    List<String> succeeded = new ArrayList<>();
    given(listingImageStorage.upload(any()))
        .willAnswer(
            invocation -> {
              ListingImageUpload upload = invocation.getArgument(0);
              if (succeeded.size() == 3) {
                throw new ListingImageUploadException(new IllegalStateException("boom"));
              }
              succeeded.add(upload.key());
              return new StoredListingImage(upload.key(), "https://cdn/" + upload.key());
            });

    assertThatThrownBy(() -> uploader.upload(LISTING_ID, ROOM_OFFER_IDS, parts(2, List.of(2, 3))))
        .isInstanceOf(ListingImageUploadException.class);

    ArgumentCaptor<List<String>> deleted = ArgumentCaptor.captor();
    then(listingImageStorage).should().deleteQuietly(deleted.capture());
    assertThat(deleted.getValue()).containsExactlyElementsOf(succeeded);
  }

  /** 매물 저장이 실패했을 때 부르는 되돌리기다. 올린 키 전체가 대상이다. */
  @Test
  void rollback_올린_키_전체를_지운다() {
    givenStorageEchoesKey();
    UploadedListingImages uploaded =
        uploader.upload(LISTING_ID, ROOM_OFFER_IDS, parts(1, List.of(2, 2)));

    uploader.rollback(uploaded);

    then(listingImageStorage).should().deleteQuietly(uploaded.keys());
  }

  /** 파일을 열지 못하는 것도 업로드 실패다 — 저장소 장애와 같은 코드로 나가야 재시도 안내가 일관된다. */
  @Test
  void upload_내용을_열지_못하면_업로드_실패로_바꾼다() {
    ListingImageParts parts =
        new ListingImageParts(
            List.of(
                new PendingListingImage(
                    ListingImageContentType.JPEG,
                    10,
                    () -> {
                      throw new IOException("closed");
                    })),
            List.of(images(2), images(2)));

    assertThatThrownBy(() -> uploader.upload(LISTING_ID, ROOM_OFFER_IDS, parts))
        .isInstanceOf(ListingImageUploadException.class);
  }

  /** 되돌리기까지 실패해도 원래 실패 원인이 가려지지 않아야 한다 — 가려지면 왜 실패했는지 아무도 모른다. */
  @Test
  void upload_되돌리기가_실패해도_원래_예외를_던진다() {
    given(listingImageStorage.upload(any()))
        .willThrow(new ListingImageUploadException(new IllegalStateException("boom")));
    willThrow(new IllegalStateException("delete failed"))
        .given(listingImageStorage)
        .deleteQuietly(any());

    assertThatThrownBy(() -> uploader.upload(LISTING_ID, ROOM_OFFER_IDS, parts(1, List.of(2, 2))))
        .isInstanceOf(ListingImageUploadException.class);
  }

  private void givenStorageEchoesKey() {
    given(listingImageStorage.upload(any()))
        .willAnswer(
            invocation -> {
              ListingImageUpload upload = invocation.getArgument(0);
              return new StoredListingImage(upload.key(), "https://cdn/" + upload.key());
            });
  }

  private static ListingImageParts parts(int coverCount, List<Integer> roomCounts) {
    return new ListingImageParts(
        images(coverCount), roomCounts.stream().map(ListingImageUploaderTest::images).toList());
  }

  private static List<PendingListingImage> images(int count) {
    return IntStream.range(0, count)
        .mapToObj(
            i ->
                new PendingListingImage(
                    ListingImageContentType.JPEG,
                    10,
                    () -> new ByteArrayInputStream(new byte[] {(byte) i})))
        .toList();
  }
}
