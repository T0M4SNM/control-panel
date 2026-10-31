package io.github.t0m4snm.control_panel_backend.movement;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.contract.ContractService;
import io.github.t0m4snm.control_panel_backend.project.ProjectRepository;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/** Ingresos y gastos del workspace. Solo {@code ADMIN}. */
@Service
@Transactional
public class MovementService {

	private final MovementRepository movements;

	private final WorkspaceRepository workspaces;

	private final ProjectRepository projects;

	private final ContractService contracts;

	public MovementService(MovementRepository movements, WorkspaceRepository workspaces, ProjectRepository projects,
			ContractService contracts) {
		this.movements = movements;
		this.workspaces = workspaces;
		this.projects = projects;
		this.contracts = contracts;
	}

	@Transactional(readOnly = true)
	public List<MovementResponse> list(AuthenticatedUser user, UUID projectId) {
		requireAdmin(user);
		return movements.findByWorkspaceId(user.workspaceId()).stream()
				.filter(m -> projectId == null || (m.getProject() != null && m.getProject().getId().equals(projectId)))
				.map(MovementResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public MovementResponse get(AuthenticatedUser user, UUID movementId) {
		return MovementResponse.from(find(user, movementId));
	}

	public MovementResponse create(AuthenticatedUser user, MovementRequest request) {
		requireAdmin(user);
		Movement movement = new Movement(workspaces.getReferenceById(user.workspaceId()));
		apply(user, movement, request);
		return MovementResponse.from(movements.save(movement));
	}

	public MovementResponse update(AuthenticatedUser user, UUID movementId, MovementRequest request) {
		Movement movement = find(user, movementId);
		apply(user, movement, request);
		return MovementResponse.from(movement);
	}

	public void delete(AuthenticatedUser user, UUID movementId) {
		movements.delete(find(user, movementId));
	}

	private void apply(AuthenticatedUser user, Movement movement, MovementRequest request) {
		Recurrence recurrence = request.recurrenceOrDefault();
		if (request.endsOn() != null) {
			if (recurrence == Recurrence.ONE_TIME) {
				throw ApiErrors.badRequest("Un movimiento puntual no puede tener fecha de fin");
			}
			if (request.endsOn().isBefore(request.date())) {
				throw ApiErrors.badRequest("La fecha de fin no puede ser anterior a la fecha del movimiento");
			}
		}
		movement.setType(request.type());
		movement.setAmount(request.amount());
		movement.setDate(request.date());
		movement.setDescription(request.description().trim());
		movement.setRecurrence(recurrence);
		movement.setEndsOn(request.endsOn());
		movement.setProject(request.projectId() == null ? null
				: projects.findByIdAndWorkspaceId(request.projectId(), user.workspaceId())
						.orElseThrow(() -> ApiErrors.badRequest("El proyecto no existe")));
		movement.setContract(request.contractId() == null ? null : contracts.findForLink(user, request.contractId()));
	}

	private Movement find(AuthenticatedUser user, UUID movementId) {
		requireAdmin(user);
		return movements.findByIdAndWorkspaceId(movementId, user.workspaceId()).orElseThrow(ApiErrors::notFound);
	}

	private static void requireAdmin(AuthenticatedUser user) {
		if (!user.isAdmin()) {
			throw ApiErrors.forbidden();
		}
	}

}
