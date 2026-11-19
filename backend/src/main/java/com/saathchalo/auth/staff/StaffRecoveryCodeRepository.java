package com.saathchalo.auth.staff;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StaffRecoveryCodeRepository extends JpaRepository<StaffRecoveryCode, UUID> {

	List<StaffRecoveryCode> findByUserIdAndUsedAtIsNull(UUID userId);

	@Modifying
	@Query("delete from StaffRecoveryCode c where c.userId = :userId")
	int deleteForUser(UUID userId);
}
