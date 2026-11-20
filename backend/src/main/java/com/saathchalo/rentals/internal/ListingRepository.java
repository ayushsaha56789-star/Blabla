package com.saathchalo.rentals.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

	List<Listing> findByOwnerIdAndStatusNotOrderByCreatedAtDesc(UUID ownerId, String status);

	Optional<Listing> findByVehicleIdAndStatusNot(UUID vehicleId, String status);

	List<Listing> findByStatusOrderByUpdatedAtAsc(String status, Pageable page);

	List<Listing> findByStatusNotOrderByUpdatedAtDesc(String status, Pageable page);

	long countByStatus(String status);
}
