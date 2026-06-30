package com.kohere.auth.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 연락처 SMS 인증 정책값. 세입자 이메일 인증({@link EmailVerificationProperties})과 통일한다 — 인증번호 6자리·코드 TTL 5분·검증 마커
 * TTL 30분·검증 시도 5회·재발송 간격 60초(ADR-0034). 모두 잠정 기본값이며 운영 확정 필요(문서 §6).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.phone")
public class PhoneVerificationProperties {

  /** 인증번호 자릿수. */
  private int codeLength = 6;

  /** 인증번호 만료(초). */
  private long codeTtlSeconds = 300;

  /** 검증 완료 마커 보존(초) — 온보딩 토큰 만료 정도. */
  private long verifiedTtlSeconds = 1800;

  /** 검증 시도 상한(초과 시 429). */
  private int maxAttempts = 5;

  /** 재발송 최소 간격(초, 미달 시 429). */
  private long resendIntervalSeconds = 60;
}
