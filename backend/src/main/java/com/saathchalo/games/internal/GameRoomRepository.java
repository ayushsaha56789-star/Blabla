package com.saathchalo.games.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameRoomRepository extends JpaRepository<GameRoom, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from GameRoom r where r.id = :id")
	Optional<GameRoom> lockById(@Param("id") UUID id);

	Optional<GameRoom> findByCode(String code);
}
