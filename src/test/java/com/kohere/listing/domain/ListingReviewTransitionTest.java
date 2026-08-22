package com.kohere.listing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 매물 심사 상태 전이 불변식을 검증한다(US-3-7).
 *
 * <p>여기서 지키는 것은 <b>심사 대상이 {@code PENDING}뿐</b>이라는 규칙이다. 반려된 매물의 재심사는 관리자가 직접 승인하는 것이 아니라 임대인이 고쳐
 * {@code PENDING}으로 되돌린 뒤 다시 이 문을 통과한다 — 직접 승인을 열면 아무것도 고치지 않은 매물이 통과하는 우회로가 된다.
 */
class ListingReviewTransitionTest {

  private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");
  private static final Instant EARLIER = Instant.parse("2026-08-01T00:00:00Z");

  @Test
  @DisplayName("승인하면 공개 상태가 되고 갱신 시각이 바뀐다")
  void approveMakesListingPublished() {
    Listing approved = pending().approve(NOW);

    assertThat(approved.getStatus()).isEqualTo(Listing.ListingStatus.PUBLISHED);
    assertThat(approved.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("승인은 이전 반려 사유를 지운다")
  void approveClearsPreviousRejectionReason() {
    // 반려됐다 임대인이 고쳐 다시 올라온 매물은 이전 사유를 달고 PENDING으로 돌아온다.
    // 그 매물이 승인돼 공개될 때 지난 사유가 남아 있으면 안 된다.
    Listing resubmitted = pending().toBuilder().rejectionReason("주소가 일치하지 않습니다").build();

    assertThat(resubmitted.approve(NOW).getRejectionReason()).isNull();
  }

  @Test
  @DisplayName("반려하면 사유가 저장된다")
  void rejectStoresReason() {
    Listing rejected = pending().reject("사업자등록번호와 주소가 일치하지 않습니다", NOW);

    assertThat(rejected.getStatus()).isEqualTo(Listing.ListingStatus.REJECTED);
    assertThat(rejected.getRejectionReason()).isEqualTo("사업자등록번호와 주소가 일치하지 않습니다");
    assertThat(rejected.getUpdatedAt()).isEqualTo(NOW);
  }

  @ParameterizedTest
  @EnumSource(
      value = Listing.ListingStatus.class,
      names = {"PUBLISHED", "REJECTED"})
  @DisplayName("승인은 심사 대기 상태에서만 할 수 있다")
  void approveRejectsTransitionFromNonPending(Listing.ListingStatus status) {
    Listing listing = pending().toBuilder().status(status).build();

    assertThatThrownBy(() -> listing.approve(NOW))
        .isInstanceOf(ListingInvalidStatusTransitionException.class);
  }

  @ParameterizedTest
  @EnumSource(Listing.ListingStatus.class)
  @DisplayName("반려는 어느 상태에서든 할 수 있다")
  void rejectAllowedFromAnyStatus(Listing.ListingStatus status) {
    // 심사 대기 매물의 1차 반려뿐 아니라, 공개 매물을 내리는 사후 반려(PUBLISHED)와
    // 이미 반려한 매물의 사유 정정(REJECTED)이 모두 정상 경로다.
    Listing listing = pending().toBuilder().status(status).rejectionReason("이전 사유").build();

    Listing rejected = listing.reject("새 사유", NOW);

    assertThat(rejected.getStatus()).isEqualTo(Listing.ListingStatus.REJECTED);
    assertThat(rejected.getRejectionReason()).isEqualTo("새 사유");
  }

  /** 전이 검증에 필요한 필드만 채운 심사 대기 매물이다. */
  private static Listing pending() {
    return Listing.builder()
        .id("68e0000000000000000000a1")
        .schemaVersion(4)
        .status(Listing.ListingStatus.PENDING)
        .createdAt(EARLIER)
        .updatedAt(EARLIER)
        .build();
  }
}
