package io.github.t0m4snm.control_panel_backend.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.contract.ContractService;
import io.github.t0m4snm.control_panel_backend.project.ProjectMemberRepository;
import io.github.t0m4snm.control_panel_backend.project.ProjectRepository;
import io.github.t0m4snm.control_panel_backend.project.ProjectService;
import io.github.t0m4snm.control_panel_backend.server.ServerRepository;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/**
 * Dominios y subdominios del workspace.
 * <ul>
 * <li>{@code ADMIN}: ve y gestiona todos, con su contrato y vencimiento.</li>
 * <li>{@code MEMBER}: solo ve los dominios asignados a sus proyectos, sin contrato.</li>
 * </ul>
 */
@Service
@Transactional
public class DomainService {

	private final DomainRepository domains;

	private final WorkspaceRepository workspaces;

	private final ContractService contracts;

	private final ServerRepository servers;

	private final ProjectRepository projectRepository;

	private final ProjectMemberRepository members;

	private final ProjectService projects;

	public DomainService(DomainRepository domains, WorkspaceRepository workspaces, ContractService contracts,
			ServerRepository servers, ProjectRepository projectRepository, ProjectMemberRepository members,
			ProjectService projects) {
		this.domains = domains;
		this.workspaces = workspaces;
		this.contracts = contracts;
		this.servers = servers;
		this.projectRepository = projectRepository;
		this.members = members;
		this.projects = projects;
	}

	@Transactional(readOnly = true)
	public List<DomainResponse> list(AuthenticatedUser user) {
		if (user.isAdmin()) {
			return domains.findByWorkspaceIdOrderByName(user.workspaceId()).stream()
					.map(DomainResponse::forAdmin)
					.toList();
		}
		return domains.findVisibleToMember(user.id()).stream().map(DomainResponse::forMember).toList();
	}

	@Transactional(readOnly = true)
	public DomainResponse get(AuthenticatedUser user, UUID domainId) {
		Domain domain = findInWorkspace(user, domainId);
		if (user.isAdmin()) {
			return DomainResponse.forAdmin(domain);
		}
		boolean visible = domain.getProject() != null
				&& members.findByProjectIdAndUserId(domain.getProject().getId(), user.id()).isPresent();
		if (!visible) {
			throw ApiErrors.notFound();
		}
		return DomainResponse.forMember(domain);
	}

	/** Dominios de un proyecto, para cualquiera que pueda ver el proyecto. */
	@Transactional(readOnly = true)
	public List<DomainResponse> listForProject(AuthenticatedUser user, UUID projectId) {
		projects.findVisible(user, projectId);
		return domains.findByProjectIdOrderByName(projectId).stream()
				.map(user.isAdmin() ? DomainResponse::forAdmin : DomainResponse::forMember)
				.toList();
	}

	public DomainResponse create(AuthenticatedUser user, DomainRequest request) {
		requireAdmin(user);
		Domain domain = new Domain(workspaces.getReferenceById(user.workspaceId()));
		apply(user, domain, request);
		return DomainResponse.forAdmin(domains.save(domain));
	}

	public DomainResponse update(AuthenticatedUser user, UUID domainId, DomainRequest request) {
		requireAdmin(user);
		Domain domain = findInWorkspace(user, domainId);
		apply(user, domain, request);
		return DomainResponse.forAdmin(domain);
	}

	/** Borra también sus subdominios. */
	public void delete(AuthenticatedUser user, UUID domainId) {
		requireAdmin(user);
		deleteWithSubdomains(findInWorkspace(user, domainId));
	}

	private void deleteWithSubdomains(Domain domain) {
		domains.findByParentId(domain.getId()).forEach(this::deleteWithSubdomains);
		domains.delete(domain);
	}

	private void apply(AuthenticatedUser user, Domain domain, DomainRequest request) {
		String name = request.normalizedName();
		if (!name.equals(domain.getName())) {
			if (domains.existsByWorkspaceIdAndName(user.workspaceId(), name)) {
				throw ApiErrors.conflict("Ya existe el dominio " + name);
			}
			if (domain.getId() != null && !domains.findByParentId(domain.getId()).isEmpty()) {
				throw ApiErrors.conflict("No se puede renombrar un dominio con subdominios");
			}
		}

		Domain parent = null;
		if (request.parentId() != null) {
			parent = domains.findByIdAndWorkspaceId(request.parentId(), user.workspaceId())
					.orElseThrow(() -> ApiErrors.badRequest("El dominio padre no existe"));
			if (!name.endsWith("." + parent.getName())) {
				throw ApiErrors.badRequest("Un subdominio de " + parent.getName() + " debe terminar en ."
						+ parent.getName());
			}
			if (request.contractId() != null) {
				throw ApiErrors.badRequest("Los subdominios heredan el contrato del dominio padre");
			}
		}

		domain.setName(name);
		domain.setParent(parent);
		domain.setContract(request.contractId() == null ? null : contracts.findForLink(user, request.contractId()));
		domain.setServer(request.serverId() == null ? null
				: servers.findByIdAndWorkspaceId(request.serverId(), user.workspaceId())
						.orElseThrow(() -> ApiErrors.badRequest("El servidor no existe")));
		domain.setProject(request.projectId() == null ? null
				: projectRepository.findByIdAndWorkspaceId(request.projectId(), user.workspaceId())
						.orElseThrow(() -> ApiErrors.badRequest("El proyecto no existe")));
		domain.setRegistrar(blankToNull(request.registrar()));
		domain.setNotes(blankToNull(request.notes()));
	}

	private Domain findInWorkspace(AuthenticatedUser user, UUID domainId) {
		return domains.findByIdAndWorkspaceId(domainId, user.workspaceId()).orElseThrow(ApiErrors::notFound);
	}

	private static void requireAdmin(AuthenticatedUser user) {
		if (!user.isAdmin()) {
			throw ApiErrors.forbidden();
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
