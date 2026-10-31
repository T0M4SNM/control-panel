package io.github.t0m4snm.control_panel_backend.monitoring;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.domain.DomainRepository;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheckResponse.Result;
import io.github.t0m4snm.control_panel_backend.project.ProjectRepository;
import io.github.t0m4snm.control_panel_backend.project.ProjectService;
import io.github.t0m4snm.control_panel_backend.server.ProjectServerRepository;
import io.github.t0m4snm.control_panel_backend.server.ServerRepository;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/**
 * Comprobaciones de estado.
 * <ul>
 * <li>{@code ADMIN}: ve y gestiona todas las del workspace.</li>
 * <li>{@code MEMBER}: solo ve, sin poder cambiar nada, las de sus proyectos y las de los servidores
 * y dominios de sus proyectos.</li>
 * </ul>
 */
@Service
@Transactional
public class HealthCheckService {

	static final int DEFAULT_RESULTS = 50;

	static final int MAX_RESULTS = 500;

	/** Nombre de host, IPv4 o IPv6; sin esquema, ruta ni puerto. */
	private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9.\\-]+$|^\\[?[0-9A-Fa-f:.]+]?$");

	private final HealthCheckRepository checks;

	private final CheckResultRepository results;

	private final WorkspaceRepository workspaces;

	private final ServerRepository servers;

	private final DomainRepository domains;

	private final ProjectRepository projectRepository;

	private final ProjectServerRepository projectServers;

	private final ProjectService projects;

	public HealthCheckService(HealthCheckRepository checks, CheckResultRepository results,
			WorkspaceRepository workspaces, ServerRepository servers, DomainRepository domains,
			ProjectRepository projectRepository, ProjectServerRepository projectServers, ProjectService projects) {
		this.checks = checks;
		this.results = results;
		this.workspaces = workspaces;
		this.servers = servers;
		this.domains = domains;
		this.projectRepository = projectRepository;
		this.projectServers = projectServers;
		this.projects = projects;
	}

	@Transactional(readOnly = true)
	public List<HealthCheckResponse> list(AuthenticatedUser user) {
		return visible(user).stream().map(HealthCheckResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public HealthCheckResponse get(AuthenticatedUser user, UUID checkId) {
		return HealthCheckResponse.from(findVisible(user, checkId));
	}

	@Transactional(readOnly = true)
	public List<Result> results(AuthenticatedUser user, UUID checkId, Integer limit) {
		int size = limit == null ? DEFAULT_RESULTS : limit;
		if (size < 1 || size > MAX_RESULTS) {
			throw ApiErrors.badRequest("limit debe estar entre 1 y " + MAX_RESULTS);
		}
		findVisible(user, checkId);
		return results.findByHealthCheckIdOrderByCheckedAtDesc(checkId, Limit.of(size)).stream()
				.map(Result::from)
				.toList();
	}

	/** Comprobaciones relacionadas con un proyecto: suyas, de sus servidores o de sus dominios. */
	@Transactional(readOnly = true)
	public List<HealthCheckResponse> listForProject(AuthenticatedUser user, UUID projectId) {
		projects.findVisible(user, projectId);
		Set<UUID> projectServerIds = projectServers.findByWorkspaceId(user.workspaceId()).stream()
				.filter(link -> link.getProject().getId().equals(projectId))
				.map(link -> link.getServer().getId())
				.collect(Collectors.toSet());
		return visible(user).stream()
				.filter(h -> (h.getProject() != null && h.getProject().getId().equals(projectId))
						|| (h.getServer() != null && projectServerIds.contains(h.getServer().getId()))
						|| (h.getDomain() != null && h.getDomain().getProject() != null
								&& h.getDomain().getProject().getId().equals(projectId)))
				.map(HealthCheckResponse::from)
				.toList();
	}

	public HealthCheckResponse create(AuthenticatedUser user, HealthCheckRequest request) {
		requireAdmin(user);
		HealthCheck check = new HealthCheck(workspaces.getReferenceById(user.workspaceId()));
		apply(user, check, request);
		return HealthCheckResponse.from(checks.save(check));
	}

	public HealthCheckResponse update(AuthenticatedUser user, UUID checkId, HealthCheckRequest request) {
		requireAdmin(user);
		HealthCheck check = findInWorkspace(user, checkId);
		boolean targetChanged = check.getType() != request.type() || !check.getTarget().equals(request.target().trim())
				|| !Objects.equals(check.getPort(), request.port());
		apply(user, check, request);
		if (targetChanged) {
			check.resetStatus();
		}
		return HealthCheckResponse.from(check);
	}

	/** Borra también su histórico de resultados. */
	public void delete(AuthenticatedUser user, UUID checkId) {
		requireAdmin(user);
		checks.delete(findInWorkspace(user, checkId));
	}

	private void apply(AuthenticatedUser user, HealthCheck check, HealthCheckRequest request) {
		String target = request.target().trim();
		validateTarget(request.type(), target, request.port());

		check.setName(request.name().trim());
		check.setType(request.type());
		check.setTarget(target);
		check.setPort(request.port());
		check.setIntervalSeconds(request.intervalSeconds() == null ? 300 : request.intervalSeconds());
		check.setTimeoutMs(request.timeoutMs() == null ? 10_000 : request.timeoutMs());
		check.setFailureThreshold(request.failureThreshold() == null ? 3 : request.failureThreshold());
		check.setEnabled(request.enabled() == null || request.enabled());
		check.setServer(request.serverId() == null ? null
				: servers.findByIdAndWorkspaceId(request.serverId(), user.workspaceId())
						.orElseThrow(() -> ApiErrors.badRequest("El servidor no existe")));
		check.setDomain(request.domainId() == null ? null
				: domains.findByIdAndWorkspaceId(request.domainId(), user.workspaceId())
						.orElseThrow(() -> ApiErrors.badRequest("El dominio no existe")));
		check.setProject(request.projectId() == null ? null
				: projectRepository.findByIdAndWorkspaceId(request.projectId(), user.workspaceId())
						.orElseThrow(() -> ApiErrors.badRequest("El proyecto no existe")));
	}

	/**
	 * {@code HTTP} necesita una URL http(s) completa y no admite {@code port} (va en la URL). El resto
	 * necesita un host o IP; {@code TCP_PORT} exige {@code port}, {@code SSL} usa 443 si no llega y
	 * {@code PING} no lo admite.
	 */
	static void validateTarget(CheckType type, String target, Integer port) {
		if (type == CheckType.HTTP) {
			try {
				URI uri = new URI(target);
				if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
						|| uri.getHost() == null) {
					throw ApiErrors.badRequest("Una comprobación HTTP necesita una URL http:// o https://");
				}
			} catch (URISyntaxException e) {
				throw ApiErrors.badRequest("La URL no es válida");
			}
			if (port != null) {
				throw ApiErrors.badRequest("En una comprobación HTTP el puerto va dentro de la URL");
			}
			return;
		}
		if (!HOST.matcher(target).matches()) {
			throw ApiErrors.badRequest("El destino debe ser un nombre de host o una IP, sin http:// ni rutas");
		}
		if (type == CheckType.TCP_PORT && port == null) {
			throw ApiErrors.badRequest("Una comprobación TCP_PORT necesita el puerto");
		}
		if (type == CheckType.PING && port != null) {
			throw ApiErrors.badRequest("Una comprobación PING no usa puerto");
		}
	}

	private List<HealthCheck> visible(AuthenticatedUser user) {
		return user.isAdmin() ? checks.findByWorkspaceIdOrderByName(user.workspaceId())
				: checks.findVisibleToMember(user.id());
	}

	private HealthCheck findVisible(AuthenticatedUser user, UUID checkId) {
		if (user.isAdmin()) {
			return findInWorkspace(user, checkId);
		}
		return checks.findVisibleToMember(user.id()).stream()
				.filter(h -> h.getId().equals(checkId))
				.findFirst()
				.orElseThrow(ApiErrors::notFound);
	}

	private HealthCheck findInWorkspace(AuthenticatedUser user, UUID checkId) {
		return checks.findByIdAndWorkspaceId(checkId, user.workspaceId()).orElseThrow(ApiErrors::notFound);
	}

	private static void requireAdmin(AuthenticatedUser user) {
		if (!user.isAdmin()) {
			throw ApiErrors.forbidden();
		}
	}

}
