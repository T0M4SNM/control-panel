package io.github.t0m4snm.control_panel_backend.apperror;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ErrorGroupRepository extends JpaRepository<ErrorGroup, UUID> {

	/** Bloquea la fila: dos envíos del mismo error a la vez no pueden pisarse el contador. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<ErrorGroup> findByProjectIdAndFingerprint(UUID projectId, String fingerprint);

	List<ErrorGroup> findByProjectIdOrderByLastSeenAtDesc(UUID projectId, Limit limit);

	List<ErrorGroup> findByProjectIdAndStatusOrderByLastSeenAtDesc(UUID projectId, ErrorGroup.Status status, Limit limit);

	@Query("SELECT g FROM ErrorGroup g JOIN FETCH g.project WHERE g.id = :id")
	Optional<ErrorGroup> findWithProject(UUID id);

}
