package io.github.t0m4snm.control_panel_backend.server;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectServerRepository extends JpaRepository<ProjectServer, ProjectServer.Id> {

	@Query("""
			SELECT ps FROM ProjectServer ps JOIN FETCH ps.project JOIN FETCH ps.server s
			WHERE s.workspace.id = :workspaceId
			""")
	List<ProjectServer> findByWorkspaceId(UUID workspaceId);

	/** Vínculos de los proyectos donde el usuario es miembro. */
	@Query("""
			SELECT ps FROM ProjectServer ps JOIN FETCH ps.project p JOIN FETCH ps.server
			WHERE p.id IN (SELECT m.project.id FROM ProjectMember m WHERE m.user.id = :userId)
			""")
	List<ProjectServer> findVisibleToMember(UUID userId);

	@Query("SELECT ps FROM ProjectServer ps JOIN FETCH ps.project WHERE ps.server.id = :serverId")
	List<ProjectServer> findByServerId(UUID serverId);

	Optional<ProjectServer> findByProjectIdAndServerId(UUID projectId, UUID serverId);

	/** Suma de los porcentajes ya repartidos del servidor, sin contar un proyecto. */
	@Query("""
			SELECT COALESCE(SUM(ps.costSharePercent), 0) FROM ProjectServer ps
			WHERE ps.server.id = :serverId AND ps.project.id <> :excludedProjectId
			""")
	BigDecimal sumShareExcludingProject(UUID serverId, UUID excludedProjectId);

}
