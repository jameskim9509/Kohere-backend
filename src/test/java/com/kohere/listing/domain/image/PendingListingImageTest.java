package com.kohere.listing.domain.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 업로드 전에 사진 한 장을 받아들일지 정하는 규칙을 검증한다. */
class PendingListingImageTest {

  private static final PendingListingImage.ContentSource CONTENT =
      () -> new ByteArrayInputStream(new byte[] {1, 2, 3});

  @ParameterizedTest
  @ValueSource(strings = {"image/jpeg", "image/png", "image/webp", "image/heic"})
  void of_허용형식이면_받아들인다(String contentType) {
    PendingListingImage image = PendingListingImage.of(contentType, 1024, CONTENT);

    assertThat(image.contentType().mediaType()).isEqualTo(contentType);
    assertThat(image.size()).isEqualTo(1024);
  }

  /** 브라우저·SDK가 파라미터나 대문자를 붙여 보내도 같은 형식으로 본다. */
  @Test
  void of_파라미터와_대소문자를_흡수한다() {
    PendingListingImage image = PendingListingImage.of("IMAGE/JPEG; charset=binary", 10, CONTENT);

    assertThat(image.contentType()).isEqualTo(ListingImageContentType.JPEG);
  }

  /** 허용 목록 밖은 415다 — 사용자가 할 일은 다른 형식으로 변환하는 것이다. */
  @ParameterizedTest
  @ValueSource(strings = {"image/gif", "application/pdf", "text/plain"})
  void of_허용목록밖형식은_415다(String contentType) {
    assertThatThrownBy(() -> PendingListingImage.of(contentType, 10, CONTENT))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.LISTING_IMAGE_UNSUPPORTED_TYPE);
  }

  /** Content-Type이 아예 없는 part도 형식을 확인할 수 없으므로 같은 취급이다. */
  @Test
  void of_형식이_없으면_415다() {
    assertThatThrownBy(() -> PendingListingImage.of(null, 10, CONTENT))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.LISTING_IMAGE_UNSUPPORTED_TYPE);
  }

  @Test
  void of_상한을_넘으면_413이다() {
    assertThatThrownBy(
            () ->
                PendingListingImage.of(
                    "image/jpeg", PendingListingImage.MAX_SIZE_BYTES + 1, CONTENT))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.LISTING_IMAGE_TOO_LARGE);
  }

  /** 경계값은 통과한다 — 상한을 "넘는" 것만 거절한다. */
  @Test
  void of_상한과_같은_크기는_받아들인다() {
    PendingListingImage image =
        PendingListingImage.of("image/jpeg", PendingListingImage.MAX_SIZE_BYTES, CONTENT);

    assertThat(image.size()).isEqualTo(PendingListingImage.MAX_SIZE_BYTES);
  }

  /** 빈 파일은 사진을 보내지 않은 것과 같아 장수 규칙 위반으로 다룬다 — "더 작게 줄이라"는 안내가 맞지 않는다. */
  @Test
  void of_빈_파일은_장수규칙_위반이다() {
    assertThatThrownBy(() -> PendingListingImage.of("image/jpeg", 0, CONTENT))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.LISTING_IMAGE_REQUIRED);
  }
}
