package io.github.t0m4snm.control_panel_backend.project;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.user.AppUser;
import io.github.t0m4snm.control_panel_backend.user.AppUserRepository;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/**
 * Proyectos y miembros con la regla de visibilidad:
 * <ul>
 * <li>{@code ADMIN} ve y gestiona todos los proyectos de su workspace.</li>
 * <li>{@code MEMBER} solo ve los proyectos donde es miembro; {@code OWNER} y {@code EDITOR}
 * pueden editarlos y solo {@code OWNER} gestiona sus miembros.</li>
 * <li>Solo {@code ADMIN} crea y borra proyectos.</li>
 * </ul>
 * Un proyecto que el usuario no puede ver responde 404, no 403, para no revelar que existe.
 */
@Service
@Transactional
public class ProjectService {

	private final ProjectRepository projects;

	private final ProjectMemberRepository members;

	private final AppUserRepository users;

	private final WorkspaceRepository workspaces;

	public ProjectService(ProjectRepository projects, ProjectMemberRepository members, AppUserRepository users,
			WorkspaceRepository workspaces) {
		this.projects = projects;
		this.members = members;
		this.users = users;
		this.workspaces = workspaces;
	}

	@Transactional(readOnly = true)
	public List<ProjectResponse> list(AuthenticatedUser user) {
		List<ProjectMember> memberships = members.findByUserIdWithProject(user.id());
		if (!user.isAdmin()) {
			return memberships.stream()
					.map(m -> ProjectResponse.from(new ProjectAccess(m.getProject(), m.getRole(), false)))
					.toList();
		}
		Map<UUID, ProjectRole> roles = memberships.stream()
				.collect(Collectors.toMap(m -> m.getProject().getId(), ProjectMember::getRole));
		return projects.findByWorkspaceIdOrderByName(user.workspaceId()).stream()
				.map(p -> ProjectResponse.from(new ProjectAccess(p, roles.get(p.getId()), true)))
				.toList();
	}

	@Transactional(readOnly = true)
	public ProjectResponse get(AuthenticatedUser user, UUID projectId) {
		return ProjectResponse.from(access(user, projectId));
	}

	public ProjectResponse create(AuthenticatedUser user, ProjectRequest request) {
		if (!user.isAdmin()) {
			throw forbidden();
		}
		Project project = new Project(workspaces.getReferenceById(user.workspaceId()), request.name().trim());
		request.applyTo(project);
		projects.save(project);
		members.save(new ProjectMember(project, users.getReferenceById(user.id()), ProjectRole.OWNER));
		return ProjectResponse.from(new ProjectAccess(project, ProjectRole.OWNER, true));
	}

	public ProjectResponse update(AuthenticatedUser user, UUID projectId, ProjectRequest request) {
		ProjectAccess access = access(user, projectId);
		if (!access.canEdit()) {
			throw forbidden();
		}
		access.project().setName(request.name().trim());
		request.applyTo(access.project());
		projects.flush();
		return ProjectResponse.from(access);
	}

	public void delete(AuthenticatedUser user, UUID projectId) {
		ProjectAccess access = access(user, projectId);
		if (!access.canDelete()) {
			throw forbidden();
		}
		// La base ya borra en cascada, pero Hibernate no deja borrar un proyecto mientras
		// tenga miembros cargados en memoria que apunten a él
		members.deleteAll(members.findByProjectIdWithUser(projectId));
		projects.delete(access.project());
	}

	@Transactional(readOnly = true)
	public List<MemberResponse> members(AuthenticatedUser user, UUID projectId) {
		access(user, projectId);
		return members.findByProjectIdWithUser(projectId).stream().map(MemberResponse::from).toList();
	}

	/** Añade un miembro o cambia su rol si ya lo era. */
	public MemberResponse putMember(AuthenticatedUser user, UUID projectId, UUID memberUserId, ProjectRole role) {
		ProjectAccess access = access(user, projectId);
		if (!access.canManageMembers()) {
			throw forbidden();
		}
		AppUser member = users.findById(memberUserId)
				.filter(u -> u.isActive() && u.getWorkspace().getId().equals(user.workspaceId()))
				.orElseThrow(ProjectService::notFound);
		ProjectMember membership = members.findByProjectIdAndUserId(projectId, memberUserId)
				.orElseGet(() -> members.save(new ProjectMember(access.project(), member, role)));
		membership.setRole(role);
		return MemberResponse.from(membership);
	}

	public void removeMember(AuthenticatedUser user, UUID projectId, UUID memberUserId) {
		ProjectAccess access = access(user, projectId);
		if (!access.canManageMembers()) {
			throw forbidden();
		}
		ProjectMember membership = members.findByProjectIdAndUserId(projectId, memberUserId)
				.orElseThrow(ProjectService::notFound);
		members.delete(membership);
	}

	private ProjectAccess access(AuthenticatedUser user, UUID projectId) {
		if (user.isAdmin()) {
			Project project = projects.findByIdAndWorkspaceId(projectId, user.workspaceId())
					.orElseThrow(ProjectService::notFound);
			ProjectRole role = members.findByProjectIdAndUserId(projectId, user.id())
					.map(ProjectMember::getRole)
					.orElse(null);
			return new ProjectAccess(project, role, true);
		}
		ProjectMember membership = members.findByProjectIdAndUserId(projectId, user.id())
				.orElseThrow(ProjectService::notFound);
		return new ProjectAccess(membership.getProject(), membership.getRole(), false);
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	private static ResponseStatusException forbidden() {
		return new ResponseStatusException(HttpStatus.FORBIDDEN);
	}

}
