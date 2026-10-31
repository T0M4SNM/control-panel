package io.github.t0m4snm.control_panel_backend.contract;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

	List<Contract> findByWorkspaceIdOrderByRenewalDate(UUID workspaceId);

	List<Contract> findByWorkspaceIdAndStatus(UUID workspaceId, ContractStatus status);

	Optional<Contract> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

}
