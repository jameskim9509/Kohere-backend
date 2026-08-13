package com.kohere.listing.domain.image;

import java.util.List;

/**
 * 등록 요청이 실어 보낸 사진 묶음이다.
 *
 * <p>지점 대표사진 한 묶음과, 방마다 한 묶음씩을 담는다. 방 묶음의 순서가 곧 {@code roomOffers} 배열의 순서다 — 파일명이 아니라 part 이름의 인덱스로
 * 짝을 맞추기 때문이며, 파일명 규칙에 기대면 클라이언트가 이름을 바꾸는 순간 조용히 깨진다(ADR-0041 §1).
 *
 * @param coverImages 지점 대표사진. 첫 장이 카드·상세의 대표 이미지가 된다
 * @param roomImages 방별 사진. {@code roomImages.get(i)}가 {@code roomOffers[i]}의 사진이다
 */
public record ListingImageParts(
    List<PendingListingImage> coverImages, List<List<PendingListingImage>> roomImages) {

  /** 지점 대표사진 장수 범위다. */
  public static final int MIN_COVER_IMAGES = 1;

  public static final int MAX_COVER_IMAGES = 10;

  /** 방 하나가 가질 수 있는 사진 장수 범위다. 최소 2장은 v4 저장 불변식이기도 하다. */
  public static final int MIN_ROOM_IMAGES = 2;

  public static final int MAX_ROOM_IMAGES = 10;

  /**
   * 장수 규칙과 방과의 짝을 확인해 사진 묶음을 만든다.
   *
   * <p>업로드보다 먼저 불러야 한다. 거절할 요청이 저장소에 흔적을 남기지 않는 것은 검증이 앞선다는 사실에 기댄 성질이다.
   *
   * @param coverImages 지점 대표사진
   * @param roomImages 방별 사진. 방 개수와 길이가 같아야 하고 빈 묶음이 있으면 안 된다
   * @param roomOfferCount 요청 본문이 담은 방 개수
   * @throws ListingImageException 장수가 범위를 벗어나거나 방과 짝이 맞지 않는 경우
   */
  public static ListingImageParts of(
      List<PendingListingImage> coverImages,
      List<List<PendingListingImage>> roomImages,
      int roomOfferCount) {
    requireCount(coverImages.size(), MIN_COVER_IMAGES, MAX_COVER_IMAGES);
    if (roomImages.size() != roomOfferCount) {
      throw ListingImageException.partMismatch();
    }
    roomImages.forEach(images -> requireCount(images.size(), MIN_ROOM_IMAGES, MAX_ROOM_IMAGES));
    return new ListingImageParts(List.copyOf(coverImages), List.copyOf(roomImages));
  }

  private static void requireCount(int count, int min, int max) {
    if (count < min || count > max) {
      throw ListingImageException.countOutOfRange();
    }
  }
}
