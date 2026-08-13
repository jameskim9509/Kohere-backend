package com.kohere.listing.presentation.dto;

import com.kohere.listing.domain.image.ListingImageException;
import com.kohere.listing.domain.image.ListingImageParts;
import com.kohere.listing.domain.image.PendingListingImage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

/**
 * multipart 요청의 파일 part를 사진 묶음으로 읽는다.
 *
 * <p>지점 사진은 {@code listingImages}, 방 사진은 {@code roomImages{i}}로 오고 {@code i}가 {@code roomOffers}
 * 배열의 인덱스다. <b>파일명은 짝짓기에 쓰지 않는다</b> — 파일명 규칙에 기대면 클라이언트가 이름을 바꾸는 순간 조용히 깨진다(ADR-0041 §1).
 *
 * <p>서블릿 API에 닿는 유일한 지점이라 presentation에 둔다. 장수·형식·크기 규칙 자체는 도메인({@link ListingImageParts}·{@link
 * PendingListingImage})이 소유한다.
 */
public final class ListingImagePartReader {

  /** 등록 정보 JSON part 이름이다. 파일이 아니지만 클라이언트가 파일명을 붙여 보내면 파일 part로 들어온다. */
  public static final String REQUEST_PART = "request";

  /** 지점 대표사진 part 이름이다. */
  public static final String COVER_PART = "listingImages";

  /** 방 사진 part 이름의 접두사다. 뒤에 {@code roomOffers} 인덱스가 붙는다. */
  public static final String ROOM_PART_PREFIX = "roomImages";

  private ListingImagePartReader() {}

  /**
   * 파일 part를 읽어 검증된 사진 묶음을 만든다.
   *
   * @param fileParts 요청의 파일 part 전부(part 이름 → 파일 목록)
   * @param roomOfferCount 요청 본문이 담은 방 개수
   * @throws ListingImageException 장수·형식·크기 규칙 위반이거나 part 이름이 방과 맞지 않는 경우
   */
  public static ListingImageParts read(
      MultiValueMap<String, MultipartFile> fileParts, int roomOfferCount) {
    requireKnownPartNames(fileParts.keySet(), roomOfferCount);

    List<List<PendingListingImage>> roomImages = new ArrayList<>();
    for (int i = 0; i < roomOfferCount; i++) {
      roomImages.add(toPendingImages(fileParts.get(ROOM_PART_PREFIX + i)));
    }
    return ListingImageParts.of(
        toPendingImages(fileParts.get(COVER_PART)), roomImages, roomOfferCount);
  }

  /**
   * 알 수 없는 파일 part가 섞였는지 본다.
   *
   * <p>{@code roomImages7}처럼 방 개수를 넘는 인덱스를 그냥 무시하면, 임대인이 올린 사진이 조용히 사라진 채 등록이 성공한다. 짝이 맞지 않는다는 사실을
   * 알려 앱이 part 이름을 고치게 한다.
   */
  private static void requireKnownPartNames(Iterable<String> partNames, int roomOfferCount) {
    for (String partName : partNames) {
      // request part는 JSON이지만 파일명이 붙어 오면 파일 part 목록에 섞인다 — 사진 규칙의 대상이 아니다.
      if (COVER_PART.equals(partName) || REQUEST_PART.equals(partName)) {
        continue;
      }
      if (!partName.startsWith(ROOM_PART_PREFIX)) {
        throw ListingImageException.partMismatch();
      }
      int index = parseRoomIndex(partName.substring(ROOM_PART_PREFIX.length()));
      if (index < 0 || index >= roomOfferCount) {
        throw ListingImageException.partMismatch();
      }
    }
  }

  private static int parseRoomIndex(String suffix) {
    try {
      return Integer.parseInt(suffix);
    } catch (NumberFormatException e) {
      throw ListingImageException.partMismatch();
    }
  }

  private static List<PendingListingImage> toPendingImages(List<MultipartFile> files) {
    if (files == null) {
      return List.of();
    }
    return files.stream()
        .map(
            file ->
                PendingListingImage.of(file.getContentType(), file.getSize(), file::getInputStream))
        .toList();
  }
}
