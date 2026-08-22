package com.kohere.listing.application;

import com.kohere.common.response.PageResponse;
import com.kohere.listing.application.dto.AdminListingDetailResponse;
import com.kohere.listing.domain.AdminOnlyListingException;
import com.kohere.listing.domain.Listing;
import com.kohere.listing.domain.ListingNotFoundException;
import com.kohere.listing.domain.ListingRepository;
import com.kohere.user.api.UserAccountService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 매물 심사 응용 서비스다(US-3-7).
 *
 * <p><b>인가는 두 겹이다.</b> 보안 필터가 {@code /api/v1/admin/**}를 {@code hasRole("USER")}로 걸러 온보딩 토큰을 막고, 여기서
 * {@code userType=ADMIN}을 다시 확인한다. 매처는 경로 단위로만 판단할 수 있어 {@code userType}을 볼 수 없고, 토큰에 관리자 여부를 실으면
 * 권한을 회수해도 토큰 수명만큼 관리자로 남는다. 그래서 <b>판정은 매 요청 DB 조회</b>다 — 임대인 게이트({@code
 * ListingRegisterService#requireLandlord})가 이미 쓰는 방식이다.
 *
 * <p><b>승인은 {@code PENDING}에서만, 반려는 어느 상태에서든</b> 할 수 있다. 반려된 매물의 재승인은 관리자가 직접 하는 것이 아니라 임대인이 고쳐
 * {@code PENDING}으로 되돌린 뒤(수정 API는 후속) 다시 이 문을 통과한다. 반면 반려는 사후 반려(공개 매물을 내린다)와 사유 정정(이미 반려한 매물의 사유를
 * 다시 쓴다)이 모두 정상 경로라 상태를 가리지 않는다 — 노출을 여는 쪽만 좁게 통제하고 닫는 쪽은 열어 둔다. 그 가드는 도메인({@code
 * Listing#approve})이 들고 있다.
 *
 * <p>docs/api/specs/03-listings-favorites.md 「관리자 매물 심사」 · 시퀀스 US-3-7.
 */
@Service
@RequiredArgsConstructor
public class AdminListingService {

  private static final Logger log = LoggerFactory.getLogger(AdminListingService.class);

  /** 관리자만 통과시킨다. {@code user::api}가 문자열을 주므로 enum을 모듈 밖으로 흘리지 않는다. */
  private static final String USER_TYPE_ADMIN = "ADMIN";

  /** 관리자 화면은 임대인 화면과 같이 한국어 고정이다. 세입자 언어 조회를 타지 않는다. */
  private static final String ADMIN_LANGUAGE = "ko";

  private final ListingRepository listingRepository;
  private final ListingLocalizationService listingLocalizationService;
  private final UserAccountService userAccountService;

  /** 모든 상태의 매물을 조회한다. {@code statuses}가 비면 상태 조건을 걸지 않는다. */
  @Transactional(readOnly = true)
  public PageResponse<AdminListingDetailResponse> list(
      long adminId, Set<Listing.ListingStatus> statuses, int page, int size, String sort) {
    requireAdmin(adminId);

    PageResponse<Listing> found = listingRepository.findForAdmin(statuses, page, size, sort);
    List<AdminListingDetailResponse> content =
        found.content().stream().map(this::toResponse).toList();
    return new PageResponse<>(content, found.page());
  }

  /** 심사 상세. 상태와 무관하게 조회된다. */
  @Transactional(readOnly = true)
  public AdminListingDetailResponse detail(long adminId, String listingId) {
    requireAdmin(adminId);
    return toResponse(findListing(listingId));
  }

  /** 심사를 통과시켜 공개한다. 승인 직후부터 세입자 조회에 나타난다. */
  @Transactional
  public AdminListingDetailResponse approve(long adminId, String listingId) {
    requireAdmin(adminId);

    Listing approved = listingRepository.save(findListing(listingId).approve(Instant.now()));
    log.info("[ADMIN] 매물 승인 (adminId={}, listingId={})", adminId, listingId);
    return toResponse(approved);
  }

  /**
   * 사유와 함께 반려한다. 사유는 임대인만 읽는 값이라 번역하지 않는다.
   *
   * <p>상태를 가리지 않는다 — 공개 매물을 내리는 사후 반려와 이미 반려한 매물의 사유 정정도 이 경로다.
   */
  @Transactional
  public AdminListingDetailResponse reject(long adminId, String listingId, String reason) {
    requireAdmin(adminId);

    Listing rejected = listingRepository.save(findListing(listingId).reject(reason, Instant.now()));
    log.info("[ADMIN] 매물 반려 (adminId={}, listingId={})", adminId, listingId);
    return toResponse(rejected);
  }

  /**
   * 관리자 여부를 DB로 확인한다. 모든 public 메서드의 첫 줄이다.
   *
   * <p>보안 매처가 이미 온보딩 완료까지는 걸렀지만 그것으로는 역할을 알 수 없다. 여기서 확인하므로 권한을 회수하면 살아 있는 토큰으로도 심사할 수 없다.
   */
  private void requireAdmin(long adminId) {
    if (!USER_TYPE_ADMIN.equals(userAccountService.getUserType(adminId))) {
      throw new AdminOnlyListingException();
    }
  }

  private Listing findListing(String listingId) {
    return listingRepository.findById(listingId).orElseThrow(ListingNotFoundException::new);
  }

  /**
   * 심사 응답으로 변환한다.
   *
   * <p>세입자 응답을 만드는 {@link ListingResponseMapper#toDetail}을 그대로 쓰고 그것이 감추는 값을 더한다. {@code
   * favorited}는 관리자에게 의미가 없어 {@code false} 고정이다.
   */
  private AdminListingDetailResponse toResponse(Listing listing) {
    ListingLocalizationContext localization = listingLocalizationService.contextFor(ADMIN_LANGUAGE);
    return AdminListingDetailResponse.of(
        listing, ListingResponseMapper.toDetail(listing, false, localization));
  }
}
