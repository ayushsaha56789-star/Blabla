package com.saathchalo.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

	Optional<OtpChallenge> findFirstByPhoneOrderByCreatedAtDesc(String phone);

	/** Locks the row so two simultaneous guesses cannot both pass the attempt limit. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from OtpChallenge c where c.phone = :phone order by c.createdAt desc limit 1")
	Optional<OtpChallenge> findLatestForUpdate(String phone);

	long countByPhoneAndCreatedAtAfter(String phone, Instant after);

	@Modifying
	@Query("delete from OtpChallenge c where c.createdAt < :before")
	int deleteCreatedBefore(Instant before);
}
