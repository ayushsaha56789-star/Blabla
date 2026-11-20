package com.saathchalo.moderation.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, UUID> {

	List<Report> findByStatusOrderByCreatedAtAsc(String status);

	List<Report> findByStatusNotOrderByResolvedAtDesc(String status, Pageable page);

	List<Report> findByTargetTypeAndTargetIdAndStatus(String targetType, UUID targetId, String status);

	boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(UUID reporterId, String targetType, UUID targetId, String status);

	long countByReporterIdAndCreatedAtAfter(UUID reporterId, Instant after);

	long countByStatus(String status);

	long countByStatusAndCreatedAtBefore(String status, Instant before);

	@Query("select r.reportedUserId, count(r) from Report r where r.reportedUserId in :ids group by r.reportedUserId")
	List<Object[]> countAboutMembers(@Param("ids") Collection<UUID> ids);
}
