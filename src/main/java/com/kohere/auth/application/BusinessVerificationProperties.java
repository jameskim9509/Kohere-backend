package com.kohere.auth.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사업자등록번호 검증 정책값(app.business). 검증 완료 마커 보존(초) — 온보딩 토큰 만료 정도(연락처·이메일 인증 마커와 통일). 외부 provider
 * 설정(API 키·엔드포인트)은 인프라의 {@code BiznoProperties}(app.bizno)에 둔다(레이어 분리, ADR-0033).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.business")
public class BusinessVerificationProperties {

  /** 검증 완료 마커 보존(초). */
  private long verifiedTtlSeconds = 1800;
}
