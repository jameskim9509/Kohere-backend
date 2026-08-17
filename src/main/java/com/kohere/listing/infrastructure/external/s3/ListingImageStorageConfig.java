package com.kohere.listing.infrastructure.external.s3;

import com.kohere.listing.domain.image.ListingImageStorage;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * 매물 사진 저장 포트({@link ListingImageStorage}) 빈 구성이다.
 *
 * <p>{@code app.images.enabled=true}면 실제 저장소 어댑터를, 아니면 스텁 폴백을 등록한다 — 사업자번호 검증({@code
 * BiznoClientConfig})과 같은 구조다. 로컬 MinIO도 "실제 저장소"에 해당하며 endpoint만 다르다(ADR-0041 §5).
 *
 * <p>AWS와 MinIO의 차이는 셋뿐이다 — endpoint 지정, 경로 스타일 접근, 자격증명 출처. 그 셋만 분기하고 나머지는 공유해 두 환경이 서로 다른 코드 경로를
 * 타지 않게 한다.
 */
@Configuration
public class ListingImageStorageConfig {

  /** 업로드가 응답 스레드를 오래 잡지 않도록 SDK 호출 전체에 상한을 둔다. */
  private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(30);

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

  @Bean
  @ConditionalOnProperty(prefix = "app.images", name = "enabled", havingValue = "true")
  ListingImageStorage s3ListingImageStorage(ListingImageProperties properties) {
    return new S3ListingImageStorage(s3Client(properties), properties);
  }

  @Bean
  @ConditionalOnMissingBean(ListingImageStorage.class)
  ListingImageStorage stubListingImageStorage(ListingImageProperties properties) {
    return new StubListingImageStorage(properties.publicBaseUrl());
  }

  private static S3Client s3Client(ListingImageProperties properties) {
    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(properties.getRegion()))
            .httpClient(
                UrlConnectionHttpClient.builder().connectionTimeout(CONNECT_TIMEOUT).build())
            .overrideConfiguration(
                ClientOverrideConfiguration.builder().apiCallTimeout(API_CALL_TIMEOUT).build())
            .credentialsProvider(credentialsProvider(properties));

    if (StringUtils.hasText(properties.getEndpoint())) {
      builder.endpointOverride(URI.create(properties.getEndpoint()));
    }
    if (properties.isPathStyleAccess()) {
      builder.forcePathStyle(true);
    }
    return builder.build();
  }

  /**
   * 자격증명 출처를 고른다.
   *
   * <p>배포 환경은 태스크 역할이 임시 자격증명을 주므로 설정에 키를 두지 않는다({@link DefaultCredentialsProvider}). MinIO는 역할이 없어
   * 고정 키가 필요하고, 그 값이 설정에 있을 때만 정적 제공자로 바꾼다.
   */
  private static AwsCredentialsProvider credentialsProvider(ListingImageProperties properties) {
    if (StringUtils.hasText(properties.getAccessKey())
        && StringUtils.hasText(properties.getSecretKey())) {
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
    }
    return DefaultCredentialsProvider.builder().build();
  }
}
