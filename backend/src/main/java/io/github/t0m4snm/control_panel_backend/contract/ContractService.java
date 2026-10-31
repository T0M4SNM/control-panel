package io.github.t0m4snm.control_panel_backend.contract;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.server.ServerRepository;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/**
 * Contratos del workspace. Solo los ve y gestiona el {@code ADMIN}; {@code SecurityConfig} ya
 * bloquea {@code /api/contracts} a los demás, y aquí se comprueba otra vez por si se reutiliza.
 */
@Service
@Transactional
public class ContractService {

	private final ContractRepository contracts;

	private final WorkspaceRepository workspaces;

	private final ServerRepository servers;

	public ContractService(ContractRepository contracts, WorkspaceRepository workspaces, ServerRepository servers) {
		this.contracts = contracts;
		this.workspaces = workspaces;
		this.servers = servers;
	}

	@Transactional(readOnly = true)
	public List<ContractResponse> list(AuthenticatedUser user) {
		requireAdmin(user);
		return contracts.findByWorkspaceIdOrderByRenewalDate(user.workspaceId()).stream()
				.map(ContractResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ContractResponse get(AuthenticatedUser user, UUID contractId) {
		return ContractResponse.from(find(user, contractId));
	}

	public ContractResponse create(AuthenticatedUser user, ContractRequest request) {
		requireAdmin(user);
		Contract contract = new Contract(workspaces.getReferenceById(user.workspaceId()));
		request.applyTo(contract);
		return ContractResponse.from(contracts.save(contract));
	}

	public ContractResponse update(AuthenticatedUser user, UUID contractId, ContractRequest request) {
		Contract contract = find(user, contractId);
		request.applyTo(contract);
		return ContractResponse.from(contract);
	}

	/** Los servidores vinculados se quedan sin contrato. */
	public void delete(AuthenticatedUser user, UUID contractId) {
		Contract contract = find(user, contractId);
		// La base ya pone la FK a nulo, pero Hibernate no deja borrar el contrato si hay
		// servidores cargados en memoria que apuntan a él
		servers.findByContractId(contractId).forEach(server -> server.setContract(null));
		contracts.delete(contract);
	}

	/** Contrato del workspace del usuario, para vincularlo desde otros recursos. */
	@Transactional(readOnly = true)
	public Contract findForLink(AuthenticatedUser user, UUID contractId) {
		return contracts.findByIdAndWorkspaceId(contractId, user.workspaceId())
				.orElseThrow(() -> ApiErrors.badRequest("El contrato no existe"));
	}

	private Contract find(AuthenticatedUser user, UUID contractId) {
		requireAdmin(user);
		return contracts.findByIdAndWorkspaceId(contractId, user.workspaceId()).orElseThrow(ApiErrors::notFound);
	}

	private static void requireAdmin(AuthenticatedUser user) {
		if (!user.isAdmin()) {
			throw ApiErrors.forbidden();
		}
	}

}
