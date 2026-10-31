package io.github.t0m4snm.control_panel_backend.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMember.Id> {

	@Query("""
			SELECT m FROM ProjectMember m JOIN FETCH m.project
			WHERE m.user.id = :userId
			ORDER BY m.project.name
			""")
	List<ProjectMember> findByUserIdWithProject(UUID userId);

	@Query("""
			SELECT m FROM ProjectMember m JOIN FETCH m.user
			WHERE m.project.id = :projectId
			ORDER BY m.user.displayName
			""")
	List<ProjectMember> findByProjectIdWithUser(UUID projectId);

	Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

}
