package com.kohere.listing.domain.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** 등록 요청이 참조한 사진 키의 장수와 소유권을 검증한다. 저장소를 부르기 전에 끝나야 하는 검사다. */
class ListingImageKeySetTest {

  private static final long LANDLORD_ID = 42L;

  @Test
  void of_하한을_만족하면_통과한다() {
    ListingImageKeySet keys = ListingImageKeySet.of(LANDLORD_ID, mine(1), List.of(mine(2)));

    assertThat(keys.coverKeys()).hasSize(1);
    assertThat(keys.roomKeys()).hasSize(1);
    assertThat(keys.allKeys()).hasSize(3);
  }

  @Test
  void of_상한까지_받아들인다() {
    assertThatCode(() -> ListingImageKeySet.of(LANDLORD_ID, mine(5), List.of(mine(5))))
        .doesNotThrowAnyException();
  }

  @Test
  void of_지점사진이_없으면_장수규칙_위반이다() {
    assertThatThrownBy(() -> ListingImageKeySet.of(LANDLORD_ID, mine(0), List.of(mine(2))))
        .satisfies(hasCode(ErrorCode.LISTING_IMAGE_REQUIRED));
  }

  /** 상한이 10에서 5로 내려왔다 — 6장은 이제 거절이다. */
  @Test
  void of_지점사진이_다섯장을_넘으면_장수규칙_위반이다() {
    assertThatThrownBy(() -> ListingImageKeySet.of(LANDLORD_ID, mine(6), List.of(mine(2))))
        .satisfies(hasCode(ErrorCode.LISTING_IMAGE_REQUIRED));
  }

  /** 방 사진 최소 2장은 v4 저장 불변식이기도 하다 — 여기서 막지 못하면 저장 직전에 다시 걸린다. */
  @Test
  void of_방사진이_두장미만이면_장수규칙_위반이다() {
    assertThatThrownBy(() -> ListingImageKeySet.of(LANDLORD_ID, mine(1), List.of(mine(1))))
        .satisfies(hasCode(ErrorCode.LISTING_IMAGE_REQUIRED));
  }

  @Test
  void of_방사진이_다섯장을_넘으면_장수규칙_위반이다() {
    assertThatThrownBy(() -> ListingImageKeySet.of(LANDLORD_ID, mine(1), List.of(mine(6))))
        .satisfies(hasCode(ErrorCode.LISTING_IMAGE_REQUIRED));
  }

  /** 소유권 검사가 없으면 남이 올린 사진을 자기 매물에 붙일 수 있다 — 그 키는 실재하므로 복사가 성공해 버린다. */
  @Test
  void of_남의_키가_섞이면_키_오류다() {
    List<String> withOthers = List.of(mine(1).get(0), "uploads/99/stolen.jpg");

    assertThatThrownBy(() -> ListingImageKeySet.of(LANDLORD_ID, withOthers, List.of(mine(2))))
        .satisfies(hasCode(ErrorCode.LISTING_IMAGE_KEY_NOT_FOUND));
  }

  /** 방 사진 쪽에 섞여도 마찬가지다. */
  @Test
  void of_방_사진에_남의_키가_섞여도_키_오류다() {
    List<String> roomWithOther = List.of(mine(1).get(0), "uploads/99/stolen.jpg");

    assertThatThrownBy(() -> ListingImageKeySet.of(LANDLORD_ID, mine(1), List.of(roomWithOther)))
        .satisfies(hasCode(ErrorCode.LISTING_IMAGE_KEY_NOT_FOUND));
  }

  /** 확정 위치의 키를 그대로 보내는 것도 막는다 — 남의 매물 사진을 재사용할 여지를 없앤다. */
  @Test
  void of_확정_키를_보내면_키_오류다() {
    List<String> confirmed = List.of("listings/68e0000000000000000000a1/cover/abc.jpg");

    assertThatThrownBy(() -> ListingImageKeySet.of(LANDLORD_ID, confirmed, List.of(mine(2))))
        .satisfies(hasCode(ErrorCode.LISTING_IMAGE_KEY_NOT_FOUND));
  }

  /** 방 묶음의 순서가 곧 roomOffers 순서다 — 뒤섞이면 남의 방 사진이 붙는다. */
  @Test
  void of_방_묶음의_순서를_유지한다() {
    List<String> first = mine(2);
    List<String> second = mine(3);

    ListingImageKeySet keys = ListingImageKeySet.of(LANDLORD_ID, mine(1), List.of(first, second));

    assertThat(keys.roomKeys().get(0)).containsExactlyElementsOf(first);
    assertThat(keys.roomKeys().get(1)).containsExactlyElementsOf(second);
  }

  private static Consumer<Throwable> hasCode(ErrorCode expected) {
    return throwable ->
        assertThat(throwable)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(expected);
  }

  private static List<String> mine(int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> ListingImageKeys.pending(LANDLORD_ID, ListingImageContentType.JPEG))
        .toList();
  }
}
