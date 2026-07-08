package com.kohere.booking.infrastructure;

import com.kohere.booking.domain.Booking;
import com.kohere.booking.domain.BookingRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * 예약 영속 어댑터. 도메인 포트 {@link BookingRepository}를 구현하고 Spring Data JPA에 위임한다. 도메인↔엔티티 변환은 private
 * static 컨버터로 처리한다(docs/convention/code-style.md §3-3).
 */
@Repository
@RequiredArgsConstructor
public class BookingRepositoryImpl implements BookingRepository {

  private final BookingJpaRepository jpaRepository;

  @Override
  public Booking save(Booking booking) {
    return toDomain(jpaRepository.save(toEntity(booking)));
  }

  @Override
  public List<Booking> findByTenantId(Long tenantId, int page, int size) {
    return jpaRepository
        .findByTenantId(
            tenantId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
        .stream()
        .map(BookingRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  public long countByTenantId(Long tenantId) {
    return jpaRepository.countByTenantId(tenantId);
  }

  @Override
  public Optional<Booking> findByIdAndTenantId(Long id, Long tenantId) {
    return jpaRepository.findByIdAndTenantId(id, tenantId).map(BookingRepositoryImpl::toDomain);
  }

  @Override
  public List<Booking> findByLandlordId(Long landlordId, int page, int size) {
    return jpaRepository
        .findByLandlordId(
            landlordId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
        .stream()
        .map(BookingRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  public long countByLandlordId(Long landlordId) {
    return jpaRepository.countByLandlordId(landlordId);
  }

  @Override
  public Optional<Booking> findByIdAndLandlordId(Long id, Long landlordId) {
    return jpaRepository.findByIdAndLandlordId(id, landlordId).map(BookingRepositoryImpl::toDomain);
  }

  private static Booking toDomain(BookingJpaEntity e) {
    return Booking.builder()
        .id(e.getId())
        .tenantId(e.getTenantId())
        .listingId(e.getListingId())
        .roomOfferId(e.getRoomOfferId())
        .landlordId(e.getLandlordId())
        .moveInDate(e.getMoveInDate())
        .contractPeriod(e.getContractPeriod())
        .status(e.getStatus())
        .createdAt(e.getCreatedAt())
        .build();
  }

  private static BookingJpaEntity toEntity(Booking b) {
    return BookingJpaEntity.builder()
        .id(b.getId())
        .tenantId(b.getTenantId())
        .listingId(b.getListingId())
        .roomOfferId(b.getRoomOfferId())
        .landlordId(b.getLandlordId())
        .moveInDate(b.getMoveInDate())
        .contractPeriod(b.getContractPeriod())
        .status(b.getStatus())
        .createdAt(b.getCreatedAt())
        .build();
  }
}
