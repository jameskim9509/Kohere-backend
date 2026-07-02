package com.kohere.booking.application;

import com.kohere.booking.application.dto.BookingDetailResponse;
import com.kohere.booking.application.dto.BookingResponse;
import com.kohere.booking.application.dto.BookingSummaryResponse;
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

  @Transactional(readOnly = true)
  public PageResponse<BookingSummaryResponse> getMyBookings(long tenantId, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

    List<BookingSummaryResponse> content =
        bookingRepository.findByTenantId(tenantId, safePage, safeSize).stream()
            .map(this::toSummary)
            .toList();
    long total = bookingRepository.countByTenantId(tenantId);

    int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
    boolean hasNext = safePage + 1 < totalPages;
    return PageResponse.of(content, new PageInfo(safePage, safeSize, total, totalPages, hasNext));
  }

  @Transactional(readOnly = true)
  public BookingDetailResponse getBooking(long tenantId, long bookingId) {
    Booking booking =
        bookingRepository
            .findByIdAndTenantId(bookingId, tenantId)
            .orElseThrow(BookingNotFoundException::new);
    RoomOfferBookingView offer =
        listingQueryService
            .findPublishedRoomOffer(booking.getListingId(), booking.getRoomOfferId())
            .orElse(null);
    return toDetail(booking, offer, userAccountService.getUserName(tenantId));
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
}
