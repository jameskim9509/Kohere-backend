package com.kohere.listing.infrastructure.external.s3;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 콘텐츠 이미지 저장소 설정({@code app.images}, ADR-0041).
 *
 * <p>{@code bucket}·{@code cdnDomain}은 배포 환경에 {@code APP_IMAGES_BUCKET}·{@code
 * APP_IMAGES_CDN_DOMAIN}으로 이미 주입되고 있다(terraform {@code modules/{dev,prod}}). 비밀값이 아니라 SSM이 아닌 평문
 * 환경변수다 — 자격증명은 태스크 역할이 주므로 키를 설정에 두지 않는다.
 *
 * <p>쓰기와 읽기가 서로 다른 이름을 쓴다. 업로드는 버킷 이름으로 하고, 문서에 저장하는 URL은 CDN 도메인으로 만든다 — 버킷이 비공개(OAC)라 S3 URL로는
 * 읽히지 않기 때문이다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.images")
public class ListingImageProperties {

  /** 실 저장소 사용 여부. {@code false}면 업로드를 흉내만 내는 스텁이 등록된다(로컬 초기 개발·테스트). */
  private boolean enabled = false;

  /** 업로드 대상 버킷 이름. */
  private String bucket;

  /** 저장한 사진을 읽을 도메인(호스트만, 스킴·슬래시 없이). 예 {@code cdn.kohere.app}. */
  private String cdnDomain;

  /**
   * 읽기 URL의 앞부분을 통째로 지정한다. 비우면 {@code https://{cdnDomain}}이다.
   *
   * <p>CloudFront 뒤에 있는 배포 환경은 도메인만 알면 되지만, 로컬 MinIO는 스킴이 http이고 경로 스타일이라 버킷 이름이 경로에 붙는다({@code
   * http://localhost:9000/kohere-local-images}). 그 차이를 이 값 하나로 흡수해 배포 환경 설정({@code
   * APP_IMAGES_CDN_DOMAIN})은 그대로 둔다.
   */
  private String publicBaseUrl;

  /** 버킷 리전. */
  private String region = "ap-northeast-2";

  /** S3 호환 저장소의 endpoint. 비우면 실제 AWS S3를 쓴다. 로컬 MinIO만 채운다. */
  private String endpoint;

  /**
   * 경로 스타일 접근 사용 여부.
   *
   * <p>MinIO는 가상 호스트 스타일({@code bucket.host})을 쓰면 {@code localhost} 앞에 버킷 이름이 붙은 호스트를 DNS로 찾지 못해
   * 실패한다. 로컬만 {@code true}다.
   */
  private boolean pathStyleAccess = false;

  /** MinIO 접속 키. AWS에서는 비워 둔다 — 태스크 역할의 임시 자격증명을 쓴다. */
  private String accessKey;

  /** MinIO 비밀 키. AWS에서는 비워 둔다. */
  private String secretKey;

  /**
   * 저장 키 앞에 붙일 읽기 URL을 만든다.
   *
   * @return 끝에 슬래시가 없는 URL 앞부분
   */
  public String publicBaseUrl() {
    return StringUtils.hasText(publicBaseUrl)
        ? publicBaseUrl.replaceAll("/+$", "")
        : "https://" + cdnDomain;
  }
}
