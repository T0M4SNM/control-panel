package io.github.t0m4snm.control_panel_backend.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

	List<Project> findByWorkspaceIdOrderByName(UUID workspaceId);

	Optional<Project> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

}
