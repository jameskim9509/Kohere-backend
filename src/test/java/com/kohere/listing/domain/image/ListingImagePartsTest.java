package com.kohere.listing.domain.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** 사진 장수 규칙과 방과의 짝을 검증한다. */
class ListingImagePartsTest {

  @Test
  void of_하한을_만족하면_통과한다() {
    ListingImageParts parts = ListingImageParts.of(images(1), List.of(images(2)), 1);

    assertThat(parts.coverImages()).hasSize(1);
    assertThat(parts.roomImages()).hasSize(1);
  }

  @Test
  void of_상한까지_받아들인다() {
    assertThatCode(() -> ListingImageParts.of(images(10), List.of(images(10)), 1))
        .doesNotThrowAnyException();
  }

  @Test
  void of_지점사진이_없으면_장수규칙_위반이다() {
    assertThatThrownBy(() -> ListingImageParts.of(images(0), List.of(images(2)), 1))
        .satisfies(hasCode(ErrorCode.LISTING_IMAGE_REQUIRED));
  }

  @Test
  void of_지점사진이_상한을_넘으면_장수규칙_위반이다() {
    assertThatThrownBy(() -> ListingImageParts.of(images(11), List.of(images(2)), 1))
        .satisfies(hasCode(ErrorCode.LISTING_IMAGE_REQUIRED));
  }

  /** 방 사진 최소 2장은 v4 저장 불변식이기도 하다 — 여기서 막지 못하면 저장 직전에 다시 걸린다. */
  @Test
  void of_방사진이_두장미만이면_장수규칙_위반이다() {
    assertThatThrownBy(() -> ListingImageParts.of(images(1), List.of(images(1)), 1))
        .satisfies(hasCode(ErrorCode.LISTING_IMAGE_REQUIRED));
  }

  /** 사진이 오지 않은 방이 있으면 짝이 맞지 않는다 — 방 수와 묶음 수가 같아야 한다. */
  @Test
  void of_방보다_사진묶음이_적으면_짝이_맞지_않는다() {
    assertThatThrownBy(() -> ListingImageParts.of(images(1), List.of(images(2)), 2))
        .satisfies(hasCode(ErrorCode.LISTING_IMAGE_PART_MISMATCH));
  }

  @Test
  void of_방보다_사진묶음이_많으면_짝이_맞지_않는다() {
    assertThatThrownBy(() -> ListingImageParts.of(images(1), List.of(images(2), images(2)), 1))
        .satisfies(hasCode(ErrorCode.LISTING_IMAGE_PART_MISMATCH));
  }

  /** 방 묶음의 순서가 곧 roomOffers 순서다 — 뒤섞이면 남의 방 사진이 붙는다. */
  @Test
  void of_방_묶음의_순서를_유지한다() {
    List<PendingListingImage> first = images(2);
    List<PendingListingImage> second = images(3);

    ListingImageParts parts = ListingImageParts.of(images(1), List.of(first, second), 2);

    assertThat(parts.roomImages().get(0)).containsExactlyElementsOf(first);
    assertThat(parts.roomImages().get(1)).containsExactlyElementsOf(second);
  }

  private static java.util.function.Consumer<Throwable> hasCode(ErrorCode expected) {
    return throwable ->
        assertThat(throwable)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(expected);
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
