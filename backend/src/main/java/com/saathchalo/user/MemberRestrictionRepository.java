package com.saathchalo.user;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRestrictionRepository extends JpaRepository<MemberRestriction, UUID> {

	@Query("select r from MemberRestriction r where r.userId = :userId and r.liftedAt is null")
	List<MemberRestriction> findUnlifted(UUID userId);

	@Query("select r from MemberRestriction r where r.userId in :userIds and r.liftedAt is null")
	List<MemberRestriction> findUnliftedFor(Collection<UUID> userIds);

	List<MemberRestriction> findTop30ByUserIdOrderByStartsAtDesc(UUID userId);
}
