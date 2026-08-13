package com.kohere.listing.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;
import com.kohere.listing.domain.image.ListingImageParts;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

/** part 이름으로 사진과 방을 짝짓는 규칙을 검증한다. */
class ListingImagePartReaderTest {

  private static final String IMAGE_CONTENT_TYPE = "image/jpeg";
  private static final byte[] IMAGE_BYTES = "fake-jpeg".getBytes(StandardCharsets.UTF_8);

  @Test
  void read_part_인덱스로_방과_짝짓는다() {
    MultiValueMap<String, MultipartFile> parts = new LinkedMultiValueMap<>();
    addImage(parts, ListingImagePartReader.COVER_PART);
    addImage(parts, "roomImages0");
    addImage(parts, "roomImages0");
    addImage(parts, "roomImages1");
    addImage(parts, "roomImages1");
    addImage(parts, "roomImages1");

    ListingImageParts read = ListingImagePartReader.read(parts, 2);

    assertThat(read.coverImages()).hasSize(1);
    assertThat(read.roomImages().get(0)).hasSize(2);
    assertThat(read.roomImages().get(1)).hasSize(3);
  }

  /** request part는 JSON이지만 파일명이 붙어 오면 파일 목록에 섞인다 — 사진 규칙의 대상이 아니다. */
  @Test
  void read_request_part는_사진으로_보지_않는다() {
    MultiValueMap<String, MultipartFile> parts = new LinkedMultiValueMap<>();
    parts.add(
        ListingImagePartReader.REQUEST_PART,
        new MockMultipartFile(
            ListingImagePartReader.REQUEST_PART,
            "request.json",
            "application/json",
            "{}".getBytes(StandardCharsets.UTF_8)));
    addImage(parts, ListingImagePartReader.COVER_PART);
    addImage(parts, "roomImages0");
    addImage(parts, "roomImages0");

    ListingImageParts read = ListingImagePartReader.read(parts, 1);

    assertThat(read.coverImages()).hasSize(1);
  }

  /** 방 개수를 넘는 인덱스를 무시하면 임대인이 올린 사진이 조용히 사라진 채 등록이 성공한다. */
  @Test
  void read_방_범위를_넘는_인덱스는_거절한다() {
    MultiValueMap<String, MultipartFile> parts = new LinkedMultiValueMap<>();
    addImage(parts, ListingImagePartReader.COVER_PART);
    addImage(parts, "roomImages0");
    addImage(parts, "roomImages0");
    addImage(parts, "roomImages7");

    assertThatThrownBy(() -> ListingImagePartReader.read(parts, 1))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.LISTING_IMAGE_PART_MISMATCH);
  }

  @ParameterizedTest
  @ValueSource(strings = {"roomImages", "roomImagesA", "roomImages-1", "photos0", "roomImages 0"})
  void read_알_수_없는_part이름은_거절한다(String partName) {
    MultiValueMap<String, MultipartFile> parts = new LinkedMultiValueMap<>();
    addImage(parts, ListingImagePartReader.COVER_PART);
    addImage(parts, "roomImages0");
    addImage(parts, "roomImages0");
    addImage(parts, partName);

    assertThatThrownBy(() -> ListingImagePartReader.read(parts, 1))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.LISTING_IMAGE_PART_MISMATCH);
  }

  /** 사진이 아예 오지 않은 방은 빈 묶음이 되어 장수 규칙에서 걸린다. */
  @Test
  void read_사진이_없는_방이_있으면_거절한다() {
    MultiValueMap<String, MultipartFile> parts = new LinkedMultiValueMap<>();
    addImage(parts, ListingImagePartReader.COVER_PART);
    addImage(parts, "roomImages0");
    addImage(parts, "roomImages0");

    assertThatThrownBy(() -> ListingImagePartReader.read(parts, 2))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.LISTING_IMAGE_REQUIRED);
  }

  private static void addImage(MultiValueMap<String, MultipartFile> parts, String partName) {
    parts.add(
        partName, new MockMultipartFile(partName, "photo.jpg", IMAGE_CONTENT_TYPE, IMAGE_BYTES));
  }
}
