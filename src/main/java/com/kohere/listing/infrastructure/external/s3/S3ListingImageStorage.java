package com.kohere.listing.infrastructure.external.s3;

import com.kohere.listing.domain.image.ListingImageStorage;
import com.kohere.listing.domain.image.ListingImageUpload;
import com.kohere.listing.domain.image.ListingImageUploadException;
import com.kohere.listing.domain.image.StoredListingImage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3(및 S3 호환 저장소)에 매물 사진을 올리는 어댑터다.
 *
 * <p>로컬 MinIO와 배포 환경 S3를 같은 구현으로 다룬다 — endpoint와 자격증명만 다르고 프로토콜이 같아, 로컬에서도 실제 업로드 경로를 그대로 태울 수
 * 있다(ADR-0041 §5).
 */
@Slf4j
@RequiredArgsConstructor
public class S3ListingImageStorage implements ListingImageStorage {

  /** 한 번의 DeleteObjects 요청이 받는 키 상한이다(S3 규격). */
  private static final int DELETE_BATCH_SIZE = 1000;

  private final S3Client s3Client;
  private final ListingImageProperties properties;

  @Override
  public StoredListingImage upload(ListingImageUpload image) {
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(properties.getBucket())
            .key(image.key())
            .contentType(image.contentType().mediaType())
            .contentLength(image.contentLength())
            .build();
    try {
      s3Client.putObject(
          request, RequestBody.fromInputStream(image.content(), image.contentLength()));
    } catch (RuntimeException e) {
      throw new ListingImageUploadException(e);
    }
    return new StoredListingImage(image.key(), toUrl(image.key()));
  }

  /**
   * 올린 객체를 지운다.
   *
   * <p>{@code DeleteObjects}로 한 번에 보낸다 — 보상 경로라 왕복이 늘수록 실패 확률이 올라간다. 삭제에 실패해도 예외를 던지지 않고 키를 로그에
   * 남긴다. 남은 객체는 아무 매물도 참조하지 않으므로 사용자에게는 영향이 없고, 나중에 대조 정리로 걷어낼 수 있다.
   */
  @Override
  public void deleteQuietly(List<String> keys) {
    if (keys.isEmpty()) {
      return;
    }
    for (int from = 0; from < keys.size(); from += DELETE_BATCH_SIZE) {
      List<String> batch = keys.subList(from, Math.min(from + DELETE_BATCH_SIZE, keys.size()));
      deleteBatchQuietly(batch);
    }
  }

  private void deleteBatchQuietly(List<String> keys) {
    try {
      s3Client.deleteObjects(
          DeleteObjectsRequest.builder()
              .bucket(properties.getBucket())
              .delete(
                  Delete.builder()
                      .objects(
                          keys.stream()
                              .map(key -> ObjectIdentifier.builder().key(key).build())
                              .toList())
                      .build())
              .build());
    } catch (RuntimeException e) {
      log.error("매물 사진 보상 삭제 실패 — 참조 없는 객체가 남는다. keys={}", keys, e);
    }
  }

  /** 버킷이 비공개(OAC)라 읽기 주소는 업로드 주소가 아니라 CDN 기준으로 만든다. */
  private String toUrl(String key) {
    return "%s/%s".formatted(properties.publicBaseUrl(), key);
  }
}
