package com.kohere.listing.domain.image;

import java.util.List;
import java.util.stream.Stream;

/**
 * 등록 요청이 참조한 사진 키 묶음이다.
 *
 * <p>사진 파일은 등록보다 먼저 올라가 있고, 등록 요청은 그 <b>키</b>만 가리킨다(ADR-0041 §1). 어느 사진이 어느 방 것인지는 요청 JSON의 구조가
 * 정하므로 이 타입도 같은 모양 — 커버 한 묶음과 방마다 한 묶음 — 을 갖는다.
 *
 * @param coverKeys 지점 대표사진 키. 첫 값이 카드·상세의 대표 이미지가 된다
 * @param roomKeys 방별 사진 키. {@code roomKeys.get(i)}가 {@code roomOffers[i]}의 사진이다
 */
public record ListingImageKeySet(List<String> coverKeys, List<List<String>> roomKeys) {

  /** 지점 대표사진 장수 범위다. */
  public static final int MIN_COVER_IMAGES = 1;

  public static final int MAX_COVER_IMAGES = 5;

  /** 방 하나가 가질 수 있는 사진 장수 범위다. 최소 2장은 v4 저장 불변식이기도 하다. */
  public static final int MIN_ROOM_IMAGES = 2;

  public static final int MAX_ROOM_IMAGES = 5;

  /**
   * 장수와 소유권을 확인해 키 묶음을 만든다.
   *
   * <p><b>저장소를 부르기 전에</b> 끝나는 검사다. 특히 소유권은 키 문자열에 임대인 식별자가 박혀 있어 비교만으로 판정된다 — 이게 없으면 남이 올린 사진을 자기
   * 매물에 붙일 수 있고, 그 키는 실재하므로 복사가 성공해 버린다.
   *
   * @param landlordId 요청자(토큰에서 얻은 값)
   * @param coverKeys 지점 대표사진 키
   * @param roomKeys 방별 사진 키. 바깥 리스트의 길이가 방 개수와 같아야 한다
   * @throws ListingImageException 장수가 범위를 벗어난 경우
   * @throws ListingImageKeyNotFoundException 남의 키가 섞인 경우
   */
  public static ListingImageKeySet of(
      long landlordId, List<String> coverKeys, List<List<String>> roomKeys) {
    requireCount(coverKeys.size(), MIN_COVER_IMAGES, MAX_COVER_IMAGES);
    roomKeys.forEach(keys -> requireCount(keys.size(), MIN_ROOM_IMAGES, MAX_ROOM_IMAGES));
    Stream.concat(coverKeys.stream(), roomKeys.stream().flatMap(List::stream))
        .forEach(key -> requireOwnedBy(key, landlordId));
    return new ListingImageKeySet(
        List.copyOf(coverKeys), roomKeys.stream().map(List::copyOf).toList());
  }

  /** 묶음이 담은 키 전체다. 순서는 커버 → 방 순이며 되돌릴 대상을 셀 때 쓴다. */
  public List<String> allKeys() {
    return Stream.concat(coverKeys.stream(), roomKeys.stream().flatMap(List::stream)).toList();
  }

  private static void requireCount(int count, int min, int max) {
    if (count < min || count > max) {
      throw ListingImageException.countOutOfRange();
    }
  }

  /**
   * 남의 키·없는 키·만료된 키를 <b>한 코드로</b> 돌려준다.
   *
   * <p>구분해 알려주면 남의 키가 있는지 없는지가 새어 나가고, 클라이언트가 할 일은 셋 다 같다(사진을 다시 올린다).
   */
  private static void requireOwnedBy(String key, long landlordId) {
    if (!ListingImageKeys.isPendingOf(key, landlordId)) {
      throw new ListingImageKeyNotFoundException();
    }
  }
}
