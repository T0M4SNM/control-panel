package io.github.t0m4snm.control_panel_backend.monitoring;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HealthCheckRepository extends JpaRepository<HealthCheck, UUID> {

	List<HealthCheck> findByWorkspaceIdOrderByName(UUID workspaceId);

	Optional<HealthCheck> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

	/**
	 * Comprobaciones que ve un miembro: las de sus proyectos, las de servidores vinculados a sus
	 * proyectos y las de dominios asignados a sus proyectos.
	 */
	@Query("""
			SELECT h FROM HealthCheck h LEFT JOIN h.domain d
			WHERE h.project.id IN (SELECT m.project.id FROM ProjectMember m WHERE m.user.id = :userId)
			   OR h.server.id IN (SELECT ps.server.id FROM ProjectServer ps
			                      WHERE ps.project.id IN (SELECT m.project.id FROM ProjectMember m WHERE m.user.id = :userId))
			   OR d.project.id IN (SELECT m.project.id FROM ProjectMember m WHERE m.user.id = :userId)
			ORDER BY h.name
			""")
	List<HealthCheck> findVisibleToMember(UUID userId);

}
