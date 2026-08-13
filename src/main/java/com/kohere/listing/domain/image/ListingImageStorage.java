package com.kohere.listing.domain.image;

import java.util.List;

/**
 * 매물 사진 저장 포트다. 구현은 infrastructure 계층에 둔다(docs/convention/code-style.md §3-3).
 *
 * <p>응용 계층은 S3의 버킷 이름·리전·SDK 타입을 알지 않고 이 계약만 호출한다. 로컬은 MinIO, 배포 환경은 S3지만 둘 다 같은 어댑터가 endpoint만 바꿔
 * 처리하므로 이 포트 위에서는 구분이 없다(ADR-0041 §5).
 */
public interface ListingImageStorage {

  /**
   * 사진 한 장을 올린다.
   *
   * @param image 저장 키와 내용을 담은 업로드 요청
   * @return 저장된 키와 읽기 URL
   * @throws ListingImageUploadException 저장소를 호출하지 못했거나 저장소가 실패를 응답한 경우
   */
  StoredListingImage upload(ListingImageUpload image);

  /**
   * 올린 사진을 지운다. 매물 저장이 실패했을 때 되돌리는 보상 경로다(ADR-0041 §3).
   *
   * <p>이미 실패를 처리하는 중에 불리므로 <strong>예외를 던지지 않는다.</strong> 여기서 다시 예외가 나면 원래의 실패 원인이 가려지고, 호출자가 할 수 있는
   * 일도 없다. 지우지 못한 객체는 아무도 참조하지 않는 채 남는데, 그 편이 실패 원인을 잃는 것보다 낫다.
   *
   * @param keys 지울 저장 키 목록. 비어 있으면 아무것도 하지 않는다
   */
  void deleteQuietly(List<String> keys);
}
