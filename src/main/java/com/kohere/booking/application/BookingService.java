package com.kohere.booking.application;

import com.kohere.booking.application.dto.BookingDetailResponse;
import com.kohere.booking.application.dto.BookingResponse;
import com.kohere.booking.application.dto.BookingSummaryResponse;
import com.kohere.booking.application.dto.LandlordBookingDetailResponse;
import com.kohere.booking.application.dto.LandlordBookingSummaryResponse;
import com.kohere.booking.domain.Booking;
import com.kohere.booking.domain.BookingNotFoundException;
import com.kohere.booking.domain.BookingRepository;
import com.kohere.booking.domain.BookingStatus;
import com.kohere.booking.domain.InvalidMoveInDateException;
import com.kohere.booking.domain.ListingUnavailableException;
import com.kohere.booking.domain.TenantOnlyException;
import com.kohere.booking.presentation.dto.BookingRequest;
import com.kohere.common.response.PageInfo;
import com.kohere.common.response.PageResponse;
import com.kohere.listing.api.BookingListingQueryService;
import com.kohere.listing.api.RoomOfferBookingView;
import com.kohere.user.api.ApplicantProfileView;
import com.kohere.user.api.UserAccountService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매물 예약(신청) 유스케이스. 예약 저장(생성)과 내 예약 조회(목록·단건 상세)를 조율한다. 예약은 {@code ACTIVE} 세입자 전용이며(URL 게이트 {@code
 * ROLE_USER} + 서비스 레벨 {@code TENANT} 검사), MVP의 예약은 "신청" 성격이라 중복 제한이 없다.
 *
 * <p>매물 요약·가격·예약자 성명은 예약에 스냅샷 저장하지 않고 조회 시점에 {@code listing :: api}·{@code user :: api} 공개 쿼리로 실시간
 * 조인한다(cross-store 조인 금지 · ADR-0005). 채팅방·예약 카드({@code BOOKING_CARD})·{@code BookingCreatedEvent}
 * 발행(chat 연동)은 후속·이연이라 본 유스케이스에서 수행하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

  private static final String USER_TYPE_TENANT = "TENANT";
  private static final String USER_TYPE_LANDLORD = "LANDLORD";
  private static final int MAX_PAGE_SIZE = 100;

  private final BookingRepository bookingRepository;
  private final BookingListingQueryService listingQueryService;
  private final UserAccountService userAccountService;

  @Transactional
  public BookingResponse createBooking(long tenantId, String listingId, BookingRequest request) {
    assertTenant(tenantId);
    RoomOfferBookingView offer =
        listingQueryService
            .findPublishedRoomOffer(listingId, request.roomOfferId())
            .orElseThrow(ListingUnavailableException::new);
    validateMoveInDate(request.moveInDate(), offer.nextAvailableFrom());

    Booking saved =
        bookingRepository.save(
            Booking.builder()
                .tenantId(tenantId)
                .listingId(listingId)
                .roomOfferId(request.roomOfferId())
                .landlordId(offer.landlordId())
                .moveInDate(request.moveInDate())
                .contractPeriod(request.contractPeriod())
                .status(BookingStatus.REQUESTED)
                .createdAt(Instant.now())
                .build());

    return new BookingResponse(
        saved.getId(),
        saved.getStatus(),
        saved.getListingId(),
        saved.getRoomOfferId(),
        saved.getMoveInDate(),
        saved.getContractPeriod(),
        saved.getCreatedAt());
  }

  /**
   * 예약 목록 조회(userType 분기). {@code LANDLORD}면 내 소유 매물에 신청된 예약을({@code landlord_id} 스코프), 그 외(세입자)는 내
   * 예약을 {@code createdAt} 내림차순 오프셋 페이지네이션으로 반환한다. 두 역할 모두 유효 요청이라 역할 {@code 403}은 없다. 별도 임대인 전용 API
   * 없음.
   */
  @Transactional(readOnly = true)
  public PageResponse<?> getBookings(long userId, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

    if (USER_TYPE_LANDLORD.equals(userAccountService.getUserType(userId))) {
      List<LandlordBookingSummaryResponse> content =
          bookingRepository.findByLandlordId(userId, safePage, safeSize).stream()
              .map(this::toLandlordSummary)
              .toList();
      return page(content, safePage, safeSize, bookingRepository.countByLandlordId(userId));
    }
    List<BookingSummaryResponse> content =
        bookingRepository.findByTenantId(userId, safePage, safeSize).stream()
            .map(this::toSummary)
            .toList();
    return page(content, safePage, safeSize, bookingRepository.countByTenantId(userId));
  }

  /**
   * 예약 단건 상세 조회(userType 분기). {@code LANDLORD}면 내 소유 매물에 신청된 예약을({@code landlord_id} 행 단위 확인 + 신청자
   * 프로필 조인), 그 외(세입자)는 내 예약을 반환한다. 조회 권한 밖(세입자: 타인 예약 / 임대인: 내 소유 매물 신청 아님)이거나 없으면 {@code
   * BookingNotFoundException}(404 통일)이다.
   */
  @Transactional(readOnly = true)
  public Object getBooking(long userId, long bookingId) {
    if (USER_TYPE_LANDLORD.equals(userAccountService.getUserType(userId))) {
      Booking booking =
          bookingRepository
              .findByIdAndLandlordId(bookingId, userId)
              .orElseThrow(BookingNotFoundException::new);
      return toLandlordDetail(
          booking, offerOf(booking), userAccountService.getApplicantProfile(booking.getTenantId()));
    }
    Booking booking =
        bookingRepository
            .findByIdAndTenantId(bookingId, userId)
            .orElseThrow(BookingNotFoundException::new);
    return toDetail(booking, offerOf(booking), userAccountService.getUserName(userId));
  }

  private RoomOfferBookingView offerOf(Booking booking) {
    return listingQueryService
        .findPublishedRoomOffer(booking.getListingId(), booking.getRoomOfferId())
        .orElse(null);
  }

  private static <T> PageResponse<T> page(List<T> content, int page, int size, long total) {
    int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
    boolean hasNext = page + 1 < totalPages;
    return PageResponse.of(content, new PageInfo(page, size, total, totalPages, hasNext));
  }

  private void assertTenant(long userId) {
    if (!USER_TYPE_TENANT.equals(userAccountService.getUserType(userId))) {
      throw new TenantOnlyException();
    }
  }

  private void validateMoveInDate(LocalDate moveInDate, LocalDate nextAvailableFrom) {
    LocalDate today = LocalDate.now();
    if (moveInDate.isBefore(today)
        || (nextAvailableFrom != null && moveInDate.isBefore(nextAvailableFrom))) {
      throw new InvalidMoveInDateException();
    }
  }

  private BookingSummaryResponse toSummary(Booking booking) {
    RoomOfferBookingView offer =
        listingQueryService
            .findPublishedRoomOffer(booking.getListingId(), booking.getRoomOfferId())
            .orElse(null);
    return new BookingSummaryResponse(
        booking.getId(),
        booking.getListingId(),
        offer == null ? null : offer.title(),
        offer == null ? null : offer.thumbnailUrl(),
        booking.getRoomOfferId(),
        booking.getMoveInDate(),
        booking.getContractPeriod(),
        booking.getStatus(),
        booking.getCreatedAt());
  }

  private BookingDetailResponse toDetail(
      Booking booking, RoomOfferBookingView offer, String tenantName) {
    int deposit = offer == null ? 0 : offer.deposit();
    int totalAmount =
        offer == null ? 0 : offer.deposit() + offer.monthlyRent() * booking.getContractPeriod();
    return new BookingDetailResponse(
        booking.getId(),
        booking.getStatus(),
        booking.getListingId(),
        booking.getRoomOfferId(),
        offer == null ? null : offer.title(),
        offer == null ? null : offer.thumbnailUrl(),
        offer == null ? null : offer.address(),
        offer == null ? null : offer.roomOfferName(),
        booking.getCreatedAt(),
        booking.getMoveInDate(),
        booking.getContractPeriod(),
        tenantName,
        deposit,
        totalAmount);
  }

  private LandlordBookingSummaryResponse toLandlordSummary(Booking booking) {
    RoomOfferBookingView offer = offerOf(booking);
    return new LandlordBookingSummaryResponse(
        booking.getId(),
        booking.getListingId(),
        offer == null ? null : offer.title(),
        offer == null ? null : offer.thumbnailUrl(),
        booking.getRoomOfferId(),
        offer == null ? null : offer.roomOfferName(),
        userAccountService.getUserName(booking.getTenantId()),
        booking.getMoveInDate(),
        booking.getContractPeriod(),
        booking.getStatus(),
        booking.getCreatedAt());
  }

  private LandlordBookingDetailResponse toLandlordDetail(
      Booking booking, RoomOfferBookingView offer, ApplicantProfileView applicant) {
    int deposit = offer == null ? 0 : offer.deposit();
    int totalAmount =
        offer == null ? 0 : offer.deposit() + offer.monthlyRent() * booking.getContractPeriod();
    return new LandlordBookingDetailResponse(
        booking.getId(),
        booking.getStatus(),
        booking.getListingId(),
        booking.getRoomOfferId(),
        offer == null ? null : offer.title(),
        offer == null ? null : offer.thumbnailUrl(),
        offer == null ? null : offer.address(),
        offer == null ? null : offer.roomOfferName(),
        booking.getCreatedAt(),
        booking.getMoveInDate(),
        booking.getContractPeriod(),
        applicant.userId(),
        applicant.name(),
        applicant.gender(),
        applicant.country(),
        applicant.countryName(),
        applicant.email(),
        deposit,
        totalAmount);
  }
}
