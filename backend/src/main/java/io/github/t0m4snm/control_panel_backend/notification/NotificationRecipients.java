package io.github.t0m4snm.control_panel_backend.notification;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheck;
import io.github.t0m4snm.control_panel_backend.project.ProjectMember;
import io.github.t0m4snm.control_panel_backend.project.ProjectMemberRepository;
import io.github.t0m4snm.control_panel_backend.server.ProjectServerRepository;
import io.github.t0m4snm.control_panel_backend.user.AppUser;
import io.github.t0m4snm.control_panel_backend.user.AppUserRepository;
import io.github.t0m4snm.control_panel_backend.user.UserRole;

/**
 * A quién llega cada aviso:
 * <ul>
 * <li>Contratos y costes: solo los {@code ADMIN} del workspace.</li>
 * <li>Caídas y SSL: los {@code ADMIN} y los miembros de los proyectos afectados, que son el de la
 * comprobación, el de su dominio y los que usan su servidor.</li>
 * </ul>
 * Solo usuarios activos.
 */
@Component
public class NotificationRecipients {

	private final AppUserRepository users;

	private final ProjectMemberRepository members;

	private final ProjectServerRepository projectServers;

	NotificationRecipients(AppUserRepository users, ProjectMemberRepository members,
			ProjectServerRepository projectServers) {
		this.users = users;
		this.members = members;
		this.projectServers = projectServers;
	}

	public Set<UUID> admins(UUID workspaceId) {
		Set<UUID> ids = new LinkedHashSet<>();
		users.findByWorkspaceIdAndRoleAndActiveTrue(workspaceId, UserRole.ADMIN).forEach(u -> ids.add(u.getId()));
		return ids;
	}

	/** Admins y miembros activos de un proyecto. */
	public Set<UUID> forProject(UUID workspaceId, UUID projectId) {
		Set<UUID> ids = admins(workspaceId);
		members.findByProjectIdWithUser(projectId).stream()
				.map(ProjectMember::getUser)
				.filter(AppUser::isActive)
				.forEach(u -> ids.add(u.getId()));
		return ids;
	}

	Set<UUID> forHealthCheck(HealthCheck check) {
		Set<UUID> projectIds = new LinkedHashSet<>();
		if (check.getProject() != null) {
			projectIds.add(check.getProject().getId());
		}
		if (check.getDomain() != null && check.getDomain().getProject() != null) {
			projectIds.add(check.getDomain().getProject().getId());
		}
		if (check.getServer() != null) {
			projectServers.findByServerId(check.getServer().getId())
					.forEach(link -> projectIds.add(link.getProject().getId()));
		}

		Set<UUID> ids = admins(check.getWorkspace().getId());
		for (UUID projectId : projectIds) {
			members.findByProjectIdWithUser(projectId).stream()
					.map(ProjectMember::getUser)
					.filter(AppUser::isActive)
					.forEach(u -> ids.add(u.getId()));
		}
		return ids;
	}

}
