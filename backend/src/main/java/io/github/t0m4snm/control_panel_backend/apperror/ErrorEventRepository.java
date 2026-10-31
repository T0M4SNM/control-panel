package io.github.t0m4snm.control_panel_backend.apperror;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ErrorEventRepository extends JpaRepository<ErrorEvent, UUID> {

	List<ErrorEvent> findByGroupIdOrderByOccurredAtDesc(UUID groupId, Limit limit);

	@Modifying
	@Query("DELETE FROM ErrorEvent e WHERE e.occurredAt < :before")
	int deleteOccurredBefore(Instant before);

}
