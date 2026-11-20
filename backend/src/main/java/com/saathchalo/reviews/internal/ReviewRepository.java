package com.saathchalo.reviews.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

	Page<Review> findBySubjectIdAndHiddenAtIsNullOrderByCreatedAtDesc(UUID subjectId, Pageable page);

	List<Review> findByAuthorIdAndRideIdIn(UUID authorId, Collection<UUID> rideIds);

	boolean existsByRideIdAndAuthorIdAndSubjectId(UUID rideId, UUID authorId, UUID subjectId);
}
