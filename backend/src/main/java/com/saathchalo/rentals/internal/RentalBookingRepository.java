package com.saathchalo.rentals.internal;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentalBookingRepository extends JpaRepository<RentalBooking, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from RentalBooking b where b.id = :id")
	Optional<RentalBooking> lockById(@Param("id") UUID id);

	List<RentalBooking> findByRenterIdOrderByStartsAtDesc(UUID renterId, Pageable page);

	List<RentalBooking> findByOwnerIdOrderByStartsAtDesc(UUID ownerId, Pageable page);

	List<RentalBooking> findByListingIdAndStatusIn(UUID listingId, Collection<String> statuses);

	long countByListingIdAndStatusIn(UUID listingId, Collection<String> statuses);

	long countByRenterIdAndStatusIn(UUID renterId, Collection<String> statuses);

	long countByOwnerIdAndStatusIn(UUID ownerId, Collection<String> statuses);

	List<RentalBooking> findByStatusOrderByCreatedAtDesc(String status, Pageable page);

	List<RentalBooking> findAllByOrderByCreatedAtDesc(Pageable page);

	long countByStatus(String status);
}
