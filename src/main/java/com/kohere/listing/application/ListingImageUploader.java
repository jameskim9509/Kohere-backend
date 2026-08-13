package com.kohere.listing.application;

import com.kohere.listing.domain.image.ListingImageKeys;
import com.kohere.listing.domain.image.ListingImageParts;
import com.kohere.listing.domain.image.ListingImageStorage;
import com.kohere.listing.domain.image.ListingImageUpload;
import com.kohere.listing.domain.image.ListingImageUploadException;
import com.kohere.listing.domain.image.PendingListingImage;
import com.kohere.listing.domain.image.StoredListingImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 등록 요청의 사진을 저장소에 올리고, 실패하면 올린 것을 되돌린다.
 *
 * <p>저장소와 DB에 걸친 쓰기라 분산 트랜잭션이 없다. 업로드를 먼저 하고 매물 저장이 실패하면 방금 올린 객체를 지우는 순서를 고른다 — 반대로 하면 사진 없는 매물이
 * DB에 남아 관리자·통계·후속 로직이 모두 그 거짓을 본다(ADR-0041 §3).
 */
@Component
@RequiredArgsConstructor
public class ListingImageUploader {

  private final ListingImageStorage listingImageStorage;

  /**
   * 사진을 모두 올린다. 한 장이라도 실패하면 이미 올린 것을 지우고 예외를 던진다.
   *
   * @param listingId 저장 전에 발급한 매물 식별자
   * @param roomOfferIds 저장 전에 발급한 방 식별자. {@code parts.roomImages()}와 순서가 같다
   * @param parts 장수·형식 검증을 마친 사진 묶음
   * @return 매물 문서에 넣을 URL과 되돌릴 때 쓸 키
   */
  public UploadedListingImages upload(
      String listingId, List<String> roomOfferIds, ListingImageParts parts) {
    List<StoredListingImage> uploaded = new ArrayList<>();
    try {
      List<String> coverUrls = new ArrayList<>();
      for (PendingListingImage image : parts.coverImages()) {
        coverUrls.add(
            uploadOne(ListingImageKeys.cover(listingId, image.contentType()), image, uploaded));
      }

      List<List<String>> roomUrls = new ArrayList<>();
      for (int i = 0; i < roomOfferIds.size(); i++) {
        List<String> urls = new ArrayList<>();
        for (PendingListingImage image : parts.roomImages().get(i)) {
          String key = ListingImageKeys.room(listingId, roomOfferIds.get(i), image.contentType());
          urls.add(uploadOne(key, image, uploaded));
        }
        roomUrls.add(List.copyOf(urls));
      }
      return new UploadedListingImages(
          List.copyOf(coverUrls), List.copyOf(roomUrls), keysOf(uploaded));
    } catch (RuntimeException e) {
      listingImageStorage.deleteQuietly(keysOf(uploaded));
      throw e;
    }
  }

  /** 매물 저장이 실패했을 때 올린 사진을 되돌린다. */
  public void rollback(UploadedListingImages images) {
    listingImageStorage.deleteQuietly(images.keys());
  }

  /** 한 장을 올리고 올린 목록에 더한다. 그 목록이 곧 되돌릴 대상이다. */
  private String uploadOne(
      String key, PendingListingImage image, List<StoredListingImage> uploaded) {
    StoredListingImage stored;
    try (InputStream content = image.content().open()) {
      stored =
          listingImageStorage.upload(
              new ListingImageUpload(key, image.contentType(), image.size(), content));
    } catch (IOException e) {
      throw new ListingImageUploadException(e);
    }
    uploaded.add(stored);
    return stored.url();
  }

  private static List<String> keysOf(List<StoredListingImage> uploaded) {
    return uploaded.stream().map(StoredListingImage::key).toList();
  }

  /**
   * 업로드 결과다.
   *
   * @param coverUrls 지점 대표사진 URL. 요청 순서를 유지한다
   * @param roomUrls 방별 사진 URL. 바깥 리스트의 순서가 {@code roomOffers} 순서다
   * @param keys 되돌릴 때 지울 저장 키 전체
   */
  public record UploadedListingImages(
      List<String> coverUrls, List<List<String>> roomUrls, List<String> keys) {}
}
