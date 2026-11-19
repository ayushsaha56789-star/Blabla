package com.saathchalo.access.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffMemberRepository extends JpaRepository<StaffMember, UUID> {

	long countByRoleCode(String roleCode);
}
