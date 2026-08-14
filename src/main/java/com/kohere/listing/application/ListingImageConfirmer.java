package com.kohere.listing.application;

import com.kohere.listing.domain.image.ListingImageKeySet;
import com.kohere.listing.domain.image.ListingImageKeys;
import com.kohere.listing.domain.image.ListingImageStorage;
import com.kohere.listing.domain.image.StoredListingImage;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 임시 위치에 올라와 있던 사진을 매물의 확정 위치로 옮긴다.
 *
 * <p>저장소와 DB에 걸친 쓰기라 분산 트랜잭션이 없다. <b>복사 → 저장</b> 순으로 두고 저장이 실패하면 복사본을 걷어낸다 — 반대로 하면 사진 없는 매물이 DB에
 * 남아 관리자·통계·후속 로직이 모두 그 거짓을 본다(ADR-0041 §4).
 *
 * <p>임시본은 어느 실패 경로에서도 지우지 않는다. 사용자가 같은 키로 다시 제출할 수 있고, 안 하면 {@code uploads/} prefix의 만료 규칙이 치운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListingImageConfirmer {

  private final ListingImageStorage listingImageStorage;

  /**
   * 사진을 확정 위치로 모두 복사한다. 한 장이라도 실패하면 이미 복사한 것을 지우고 예외를 던진다.
   *
   * @param listingId 저장 전에 발급한 매물 식별자
   * @param roomOfferIds 저장 전에 발급한 방 식별자. {@code keys.roomKeys()}와 순서가 같다
   * @param keys 장수·소유권 검사를 마친 키 묶음
   * @return 매물 문서에 넣을 URL과 되돌릴 때 쓸 키
   */
  public ConfirmedListingImages confirm(
      String listingId, List<String> roomOfferIds, ListingImageKeySet keys) {
    List<String> copied = new ArrayList<>();
    try {
      List<String> coverUrls = new ArrayList<>();
      for (String pendingKey : keys.coverKeys()) {
        coverUrls.add(
            copyOne(pendingKey, ListingImageKeys.confirmedCover(listingId, pendingKey), copied));
      }

      List<List<String>> roomUrls = new ArrayList<>();
      for (int i = 0; i < roomOfferIds.size(); i++) {
        List<String> urls = new ArrayList<>();
        for (String pendingKey : keys.roomKeys().get(i)) {
          String targetKey =
              ListingImageKeys.confirmedRoom(listingId, roomOfferIds.get(i), pendingKey);
          urls.add(copyOne(pendingKey, targetKey, copied));
        }
        roomUrls.add(List.copyOf(urls));
      }
      return new ConfirmedListingImages(
          List.copyOf(coverUrls), List.copyOf(roomUrls), List.copyOf(copied));
    } catch (RuntimeException e) {
      deleteQuietly(copied);
      throw e;
    }
  }

  /** 매물 저장이 실패했을 때 복사본을 되돌린다. 임시본은 남긴다 — 사용자가 그대로 다시 제출할 수 있다. */
  public void rollback(ConfirmedListingImages images) {
    deleteQuietly(images.copiedKeys());
  }

  /**
   * 저장까지 끝난 뒤 임시본을 치운다. 실패해도 만료 규칙이 대신 치우므로 재시도하지 않는다.
   *
   * <p>확정 결과가 아니라 원래의 키 묶음을 받는다 — 호출자가 이미 들고 있는 값이라 되돌려줄 이유가 없다.
   */
  public void discardPending(ListingImageKeySet keys) {
    deleteQuietly(keys.allKeys());
  }

  /**
   * 되돌리기·정리가 원래 실패를 가리지 않게 한다.
   *
   * <p>포트 계약이 이미 "예외를 던지지 않는다"지만, 어떤 구현이 계약을 어기면 그 순간 진짜 실패 원인이 사라진다 — 이 메서드는 항상 실패를 처리하는 중이거나 이미
   * 성공한 뒤에 불리므로 여기서 한 겹 더 막는다.
   */
  private void deleteQuietly(List<String> keys) {
    try {
      listingImageStorage.deleteQuietly(keys);
    } catch (RuntimeException e) {
      log.error("매물 사진 정리 실패 — 참조 없는 객체가 남는다. keys={}", keys, e);
    }
  }

  /** 한 장을 복사하고 복사 목록에 더한다. 그 목록이 곧 되돌릴 대상이다. */
  private String copyOne(String sourceKey, String targetKey, List<String> copied) {
    StoredListingImage stored = listingImageStorage.copy(sourceKey, targetKey);
    copied.add(stored.key());
    return stored.url();
  }

  /**
   * 확정 결과다.
   *
   * @param coverUrls 지점 대표사진 URL. 요청 순서를 유지한다
   * @param roomUrls 방별 사진 URL. 바깥 리스트의 순서가 {@code roomOffers} 순서다
   * @param copiedKeys 되돌릴 때 지울 확정 위치의 키
   */
  public record ConfirmedListingImages(
      List<String> coverUrls, List<List<String>> roomUrls, List<String> copiedKeys) {}
}
