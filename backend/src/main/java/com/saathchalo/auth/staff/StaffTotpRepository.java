package com.saathchalo.auth.staff;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffTotpRepository extends JpaRepository<StaffTotp, UUID> {
}
