package com.kohere.diagnosis.domain;

import java.time.Instant;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

/**
 * 진단 애그리거트 루트. 사용자가 제출한 진단 입력을 보관한다. 영속 기술(JPA)에 의존하지 않는 순수 도메인 모델이며, 영속 매핑은 infrastructure 계층의
 * 어댑터에서 처리한다(docs/convention/code-style.md §3-3).
 *
 * <p>입국 목적이 {@code STUDY}이면 {@code university}가, {@code NON_STUDY}이면 {@code district}가 채워진다(반대 필드는
 * null). 조건부 필수 검증은 표현 계층 DTO에서 수행한다.
 *
 * <p>스펙: docs/api/specs/02-diagnosis-recommendation.md. TODO: 소유권 검증·재진단 정책 등 도메인 불변식 메서드를 채운다.
 */
@Getter
@Builder
public class Diagnosis {

  private final Long id;
  private final Long userId;
  private final Region region;
  private final Purpose purpose;
  private final University university;
  private final District district;
  private final Set<DiagnosisCondition> conditions;
  private final int monthlyBudgetMax;
  private final ArcStatus arcStatus;
  private final DiagnosisStatus status;
  private final Instant submittedAt;
}
