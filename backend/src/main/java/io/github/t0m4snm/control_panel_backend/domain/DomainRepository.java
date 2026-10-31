package io.github.t0m4snm.control_panel_backend.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DomainRepository extends JpaRepository<Domain, UUID> {

	List<Domain> findByWorkspaceIdOrderByName(UUID workspaceId);

	Optional<Domain> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

	boolean existsByWorkspaceIdAndName(UUID workspaceId, String name);

	List<Domain> findByParentId(UUID parentId);

	/** Dominios de los proyectos donde el usuario es miembro. */
	@Query("""
			SELECT d FROM Domain d
			WHERE d.project.id IN (SELECT m.project.id FROM ProjectMember m WHERE m.user.id = :userId)
			ORDER BY d.name
			""")
	List<Domain> findVisibleToMember(UUID userId);

	List<Domain> findByProjectIdOrderByName(UUID projectId);

}
