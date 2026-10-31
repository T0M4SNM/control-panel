package io.github.t0m4snm.control_panel_backend.timetracking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

	@Query("SELECT t FROM TimeEntry t JOIN FETCH t.project WHERE t.user.id = :userId AND t.endedAt IS NULL")
	Optional<TimeEntry> findRunning(UUID userId);

	@Query("""
			SELECT t FROM TimeEntry t JOIN FETCH t.project JOIN FETCH t.user
			WHERE t.id = :id AND t.project.workspace.id = :workspaceId
			""")
	Optional<TimeEntry> findInWorkspace(UUID id, UUID workspaceId);

	/** Entradas del workspace que empiezan en {@code [from, to)}, de la más reciente a la más antigua. */
	@Query("""
			SELECT t FROM TimeEntry t JOIN FETCH t.project p JOIN FETCH t.user
			WHERE p.workspace.id = :workspaceId AND t.startedAt >= :from AND t.startedAt < :to
			ORDER BY t.startedAt DESC
			""")
	List<TimeEntry> findStartedBetween(UUID workspaceId, Instant from, Instant to);

}
