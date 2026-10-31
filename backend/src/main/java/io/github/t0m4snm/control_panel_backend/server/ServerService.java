package io.github.t0m4snm.control_panel_backend.server;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.contract.ContractService;
import io.github.t0m4snm.control_panel_backend.project.Project;
import io.github.t0m4snm.control_panel_backend.project.ProjectService;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/**
 * Servidores del workspace y su reparto entre proyectos.
 * <ul>
 * <li>{@code ADMIN}: ve y gestiona todos los servidores, con contrato y costes.</li>
 * <li>{@code MEMBER}: solo ve los servidores vinculados a sus proyectos, sin datos económicos.</li>
 * </ul>
 */
@Service
@Transactional
public class ServerService {

	private static final BigDecimal MAX_SHARE = BigDecimal.valueOf(100);

	private final ServerRepository servers;

	private final ProjectServerRepository links;

	private final WorkspaceRepository workspaces;

	private final ContractService contracts;

	private final ProjectService projects;

	public ServerService(ServerRepository servers, ProjectServerRepository links, WorkspaceRepository workspaces,
			ContractService contracts, ProjectService projects) {
		this.servers = servers;
		this.links = links;
		this.workspaces = workspaces;
		this.contracts = contracts;
		this.projects = projects;
	}

	@Transactional(readOnly = true)
	public List<ServerResponse> list(AuthenticatedUser user) {
		if (user.isAdmin()) {
			Map<UUID, List<ProjectServer>> byServer = groupByServer(links.findByWorkspaceId(user.workspaceId()));
			return servers.findByWorkspaceIdOrderByName(user.workspaceId()).stream()
					.map(s -> ServerResponse.forAdmin(s, byServer.getOrDefault(s.getId(), List.of())))
					.toList();
		}
		return memberView(links.findVisibleToMember(user.id()));
	}

	@Transactional(readOnly = true)
	public ServerResponse get(AuthenticatedUser user, UUID serverId) {
		if (user.isAdmin()) {
			return ServerResponse.forAdmin(findInWorkspace(user, serverId), links.findByServerId(serverId));
		}
		List<ProjectServer> visible = links.findVisibleToMember(user.id()).stream()
				.filter(link -> link.getServer().getId().equals(serverId))
				.toList();
		if (visible.isEmpty()) {
			throw ApiErrors.notFound();
		}
		return ServerResponse.forMember(visible.getFirst().getServer(), visible);
	}

	/** Servidores de un proyecto, para cualquiera que pueda ver el proyecto. */
	@Transactional(readOnly = true)
	public List<ServerResponse> listForProject(AuthenticatedUser user, UUID projectId) {
		projects.findVisible(user, projectId);
		if (user.isAdmin()) {
			Map<UUID, List<ProjectServer>> byServer = groupByServer(links.findByWorkspaceId(user.workspaceId()));
			return byServer.values().stream()
					.filter(serverLinks -> serverLinks.stream().anyMatch(l -> l.getProject().getId().equals(projectId)))
					.map(serverLinks -> ServerResponse.forAdmin(serverLinks.getFirst().getServer(), serverLinks))
					.sorted(Comparator.comparing(ServerResponse::name))
					.toList();
		}
		return memberView(links.findVisibleToMember(user.id())).stream()
				.filter(s -> s.projects().stream().anyMatch(p -> p.projectId().equals(projectId)))
				.toList();
	}

	public ServerResponse create(AuthenticatedUser user, ServerRequest request) {
		requireAdmin(user);
		Server server = new Server(workspaces.getReferenceById(user.workspaceId()));
		apply(user, server, request);
		return ServerResponse.forAdmin(servers.save(server), List.of());
	}

	public ServerResponse update(AuthenticatedUser user, UUID serverId, ServerRequest request) {
		requireAdmin(user);
		Server server = findInWorkspace(user, serverId);
		apply(user, server, request);
		return ServerResponse.forAdmin(server, links.findByServerId(serverId));
	}

	public void delete(AuthenticatedUser user, UUID serverId) {
		requireAdmin(user);
		Server server = findInWorkspace(user, serverId);
		links.deleteAll(links.findByServerId(serverId));
		servers.delete(server);
	}

	/**
	 * Vincula un servidor a un proyecto o cambia su porcentaje. La suma de los porcentajes de un
	 * servidor no puede pasar de 100; lo que falte hasta 100 queda como coste sin asignar.
	 */
	public ServerResponse putProjectLink(AuthenticatedUser user, UUID projectId, UUID serverId,
			BigDecimal costSharePercent) {
		requireAdmin(user);
		Project project = projects.findVisible(user, projectId);
		Server server = findInWorkspace(user, serverId);
		BigDecimal total = links.sumShareExcludingProject(serverId, projectId).add(costSharePercent);
		if (total.compareTo(MAX_SHARE) > 0) {
			throw ApiErrors.conflict("El reparto del servidor sumaría " + total.stripTrailingZeros().toPlainString()
					+ " %; no puede pasar de 100 %");
		}
		links.findByProjectIdAndServerId(projectId, serverId)
				.ifPresentOrElse(link -> link.setCostSharePercent(costSharePercent),
						() -> links.save(new ProjectServer(project, server, costSharePercent)));
		links.flush();
		return ServerResponse.forAdmin(server, links.findByServerId(serverId));
	}

	public void removeProjectLink(AuthenticatedUser user, UUID projectId, UUID serverId) {
		requireAdmin(user);
		projects.findVisible(user, projectId);
		findInWorkspace(user, serverId);
		links.delete(links.findByProjectIdAndServerId(projectId, serverId).orElseThrow(ApiErrors::notFound));
	}

	private void apply(AuthenticatedUser user, Server server, ServerRequest request) {
		request.applyTo(server);
		server.setContract(request.contractId() == null ? null : contracts.findForLink(user, request.contractId()));
	}

	private List<ServerResponse> memberView(List<ProjectServer> visibleLinks) {
		return groupByServer(visibleLinks).values().stream()
				.map(serverLinks -> ServerResponse.forMember(serverLinks.getFirst().getServer(), serverLinks))
				.sorted(Comparator.comparing(ServerResponse::name))
				.toList();
	}

	private static Map<UUID, List<ProjectServer>> groupByServer(List<ProjectServer> serverLinks) {
		return serverLinks.stream().collect(Collectors.groupingBy(link -> link.getServer().getId(),
				LinkedHashMap::new, Collectors.toList()));
	}

	private Server findInWorkspace(AuthenticatedUser user, UUID serverId) {
		return servers.findByIdAndWorkspaceId(serverId, user.workspaceId()).orElseThrow(ApiErrors::notFound);
	}

	private static void requireAdmin(AuthenticatedUser user) {
		if (!user.isAdmin()) {
			throw ApiErrors.forbidden();
		}
	}

}
