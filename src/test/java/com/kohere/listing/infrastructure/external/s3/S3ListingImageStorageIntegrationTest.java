package com.kohere.listing.infrastructure.external.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kohere.listing.domain.image.ListingImageContentType;
import com.kohere.listing.domain.image.ListingImageStorage;
import com.kohere.listing.domain.image.ListingImageUpload;
import com.kohere.listing.domain.image.ListingImageUploadException;
import com.kohere.listing.domain.image.StoredListingImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * 매물 사진 저장 어댑터를 실제 S3 프로토콜(MinIO)로 검증한다.
 *
 * <p>목으로는 키·URL 조립까지만 보이고 SDK 호출이 실제로 성립하는지는 알 수 없다 — 콘텐츠 길이·경로 스타일·삭제 요청의 체크섬 요구처럼 프로토콜에 닿는 부분이 여기서만 드러난다.
 * 로컬 개발도 같은 MinIO를 쓰므로 이 테스트가 로컬 기동을 함께 지켜 준다(ADR-0041 §5).
 */
@Testcontainers
class S3ListingImageStorageIntegrationTest {

  private static final String BUCKET = "kohere-test-images";
  private static final String CDN_DOMAIN = "cdn.test.kohere.app";

  @Container
  static MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2024-08-17T01-24-54Z");

  private static S3Client s3Client;
  private static ListingImageStorage storage;

  @BeforeAll
  static void setUp() {
    ListingImageProperties properties = new ListingImageProperties();
    properties.setEnabled(true);
    properties.setBucket(BUCKET);
    properties.setCdnDomain(CDN_DOMAIN);
    properties.setRegion(Region.AP_NORTHEAST_2.id());

    s3Client =
        S3Client.builder()
            .region(Region.AP_NORTHEAST_2)
            .endpointOverride(URI.create(minio.getS3URL()))
            .forcePathStyle(true)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
            .build();
    s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());

    storage = new S3ListingImageStorage(s3Client, properties);
  }

  /** 올린 객체가 그 키로 실제 존재하고, 돌려준 URL은 업로드 주소가 아니라 CDN 기준이다. */
  @Test
  void upload_객체를_저장하고_CDN_URL을_돌려준다() {
    byte[] content = "photo-bytes".getBytes(StandardCharsets.UTF_8);
    String key = "listings/aaa/cover/one.jpg";

    StoredListingImage stored = storage.upload(upload(key, content));

    assertThat(stored.key()).isEqualTo(key);
    assertThat(stored.url()).isEqualTo("https://" + CDN_DOMAIN + "/" + key);
    assertThat(objectBytes(key)).isEqualTo(content);
  }

  /** 저장한 Content-Type이 그대로 남아야 브라우저가 이미지로 렌더링한다. */
  @Test
  void upload_Content_Type을_보존한다() {
    String key = "listings/aaa/cover/two.png";

    storage.upload(
        new ListingImageUpload(
            key, ListingImageContentType.PNG, 3, new ByteArrayInputStream(new byte[] {1, 2, 3})));

    GetObjectResponse response =
        s3Client
            .getObject(
                GetObjectRequest.builder().bucket(BUCKET).key(key).build(),
                ResponseTransformer.toBytes())
            .response();
    assertThat(response.contentType()).isEqualTo(ListingImageContentType.PNG.mediaType());
  }

  /** 보상 삭제가 실제로 지워야 "참조 없는 파일이 남지 않는다"가 성립한다 — 버킷 버전 관리를 끈 이유이기도 하다. */
  @Test
  void deleteQuietly_올린_객체를_지운다() {
    String first = "listings/bbb/cover/one.jpg";
    String second = "listings/bbb/rooms/ccc/two.jpg";
    storage.upload(upload(first, new byte[] {1}));
    storage.upload(upload(second, new byte[] {2}));

    storage.deleteQuietly(List.of(first, second));

    assertThatThrownBy(() -> objectBytes(first)).isInstanceOf(NoSuchKeyException.class);
    assertThatThrownBy(() -> objectBytes(second)).isInstanceOf(NoSuchKeyException.class);
  }

  /** 되돌리기는 이미 실패를 처리하는 중에 불린다 — 없는 키를 만나도 예외를 던지면 원래 실패 원인이 가려진다. */
  @Test
  void deleteQuietly_없는_키를_만나도_던지지_않는다() {
    storage.deleteQuietly(List.of("listings/none/cover/missing.jpg"));
  }

  @Test
  void deleteQuietly_빈_목록이면_아무것도_하지_않는다() {
    storage.deleteQuietly(List.of());
  }

  /** 없는 버킷은 저장소 장애로 다뤄 502로 나간다 — 사용자 입력 문제가 아니다. */
  @Test
  void upload_저장소가_거절하면_업로드_실패로_바꾼다() {
    ListingImageProperties wrongBucket = new ListingImageProperties();
    wrongBucket.setBucket("no-such-bucket");
    wrongBucket.setCdnDomain(CDN_DOMAIN);
    ListingImageStorage broken = new S3ListingImageStorage(s3Client, wrongBucket);

    assertThatThrownBy(() -> broken.upload(upload("listings/x/cover/one.jpg", new byte[] {1})))
        .isInstanceOf(ListingImageUploadException.class);
  }

  private static ListingImageUpload upload(String key, byte[] content) {
    return new ListingImageUpload(
        key, ListingImageContentType.JPEG, content.length, new ByteArrayInputStream(content));
  }

  private static byte[] objectBytes(String key) {
    ResponseBytes<GetObjectResponse> object =
        s3Client.getObject(
            GetObjectRequest.builder().bucket(BUCKET).key(key).build(),
            ResponseTransformer.toBytes());
    return object.asByteArray();
  }
}
