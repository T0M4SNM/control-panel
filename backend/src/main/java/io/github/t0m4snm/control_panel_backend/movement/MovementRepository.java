package io.github.t0m4snm.control_panel_backend.movement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MovementRepository extends JpaRepository<Movement, UUID> {

	@Query("""
			SELECT m FROM Movement m LEFT JOIN FETCH m.project LEFT JOIN FETCH m.contract
			WHERE m.workspace.id = :workspaceId
			ORDER BY m.date DESC, m.description
			""")
	List<Movement> findByWorkspaceId(UUID workspaceId);

	Optional<Movement> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

	/** Movimientos que pueden contar en algún día entre {@code from} y {@code to}. */
	@Query("""
			SELECT m FROM Movement m LEFT JOIN FETCH m.project
			WHERE m.workspace.id = :workspaceId AND m.date <= :to
			  AND (m.endsOn IS NULL OR m.endsOn >= :from)
			  AND (m.recurrence <> io.github.t0m4snm.control_panel_backend.movement.Recurrence.ONE_TIME
			       OR m.date >= :from)
			""")
	List<Movement> findActiveBetween(UUID workspaceId, LocalDate from, LocalDate to);

}
