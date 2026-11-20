package com.saathchalo.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByPhone(String phone);

	@Query("select u.id from User u where u.deletionDueAt < :now and u.deletedAt is null order by u.deletionDueAt")
	List<UUID> findDueForErasure(Instant now, Pageable page);

	boolean existsByPhone(String phone);

	Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

	@Query("select u from User u where u.phone like :pattern order by u.createdAt desc")
	Page<User> searchByPhone(String pattern, Pageable pageable);

	@Query("""
			select u from User u
			where lower(concat(coalesce(u.firstName, ''), ' ', coalesce(u.lastName, ''))) like :pattern
			order by u.createdAt desc""")
	Page<User> searchByName(String pattern, Pageable pageable);
}
