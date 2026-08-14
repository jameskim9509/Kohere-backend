package com.kohere.listing.application;

import com.kohere.listing.application.dto.ListingImageUploadResponse;
import com.kohere.listing.domain.LandlordOnlyListingException;
import com.kohere.listing.domain.image.ListingImageKeys;
import com.kohere.listing.domain.image.ListingImageStorage;
import com.kohere.listing.domain.image.ListingImageUpload;
import com.kohere.listing.domain.image.ListingImageUploadException;
import com.kohere.listing.domain.image.PendingListingImage;
import com.kohere.listing.domain.image.StoredListingImage;
import com.kohere.user.api.UserAccountService;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 등록 폼이 고른 사진을 한 장씩 임시 위치에 올린다({@code POST /api/v2/listings/images}).
 *
 * <p>요청을 파일마다 가르는 이유는 브라우저가 <b>요청 단위로만</b> 업로드 진행률을 주기 때문이다. 한 요청에 몰아 실으면 파일별 진행률·전송 속도를 만들 수 없고,
 * 실패한 파일만 다시 올릴 수도 없다(ADR-0041 §1).
 *
 * <p>여기서 만드는 것은 매물이 아니라 <b>임시 사진</b>이다. 매물에 붙는 것은 등록 요청이 그 키를 참조할 때이며, 참조되지 않은 사진은 {@code uploads/}
 * prefix의 만료 규칙이 치운다.
 */
@Service
@RequiredArgsConstructor
public class ListingImageUploadService {

  /** {@code user::api}가 문자열로 주는 임대인 구분값이다. */
  private static final String USER_TYPE_LANDLORD = "LANDLORD";

  private final ListingImageStorage listingImageStorage;
  private final UserAccountService userAccountService;

  /**
   * 사진 한 장을 올리고 저장 키와 미리보기 URL을 돌려준다.
   *
   * <p>형식·크기 검증은 {@link PendingListingImage}가 이미 마친 상태로 들어온다 — 검증이 업로드보다 앞서므로 거절된 요청은 저장소에 흔적을 남기지
   * 않는다.
   *
   * @param landlordId 요청자(토큰에서 얻은 값). 저장 키에 박혀 소유권 표시가 된다
   */
  public ListingImageUploadResponse upload(long landlordId, PendingListingImage image) {
    requireLandlord(landlordId);
    String key = ListingImageKeys.pending(landlordId, image.contentType());
    try (InputStream content = image.content().open()) {
      StoredListingImage stored =
          listingImageStorage.upload(
              new ListingImageUpload(key, image.contentType(), image.size(), content));
      return new ListingImageUploadResponse(stored.key(), stored.url());
    } catch (IOException e) {
      throw new ListingImageUploadException(e);
    }
  }

  private void requireLandlord(long landlordId) {
    if (!USER_TYPE_LANDLORD.equals(userAccountService.getUserType(landlordId))) {
      throw new LandlordOnlyListingException();
    }
  }
}
