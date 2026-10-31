package io.github.t0m4snm.control_panel_backend.server;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerRepository extends JpaRepository<Server, UUID> {

	List<Server> findByWorkspaceIdOrderByName(UUID workspaceId);

	Optional<Server> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

	List<Server> findByContractId(UUID contractId);

}
