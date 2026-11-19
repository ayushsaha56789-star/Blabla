package com.saathchalo.auth.staff;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StaffSessionRepository extends JpaRepository<StaffSession, UUID> {

	@Modifying
	@Query("update StaffSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
	int revokeAllForUser(UUID userId, Instant now);

	@Query("select s.userId, max(s.lastSeenAt) from StaffSession s where s.userId in :userIds group by s.userId")
	List<Object[]> lastSeen(Collection<UUID> userIds);

	@Modifying
	@Query("delete from StaffSession s where s.expiresAt < :before")
	int deleteExpiredBefore(Instant before);
}
