package io.github.t0m4snm.control_panel_backend.project;

import java.time.Instant;
import java.util.UUID;

/**
 * Proyecto tal y como lo ve el usuario actual. {@code myRole} es su rol en el proyecto (nulo si es
 * un admin que no es miembro) y {@code permissions} le dice al frontend qué botones mostrar.
 */
public record ProjectResponse(
		UUID id,
		String name,
		String description,
		ProjectStatus status,
		String repoUrl,
		String publicUrl,
		String notes,
		Instant createdAt,
		Instant updatedAt,
		ProjectRole myRole,
		Permissions permissions) {

	static ProjectResponse from(ProjectAccess access) {
		Project p = access.project();
		return new ProjectResponse(p.getId(), p.getName(), p.getDescription(), p.getStatus(), p.getRepoUrl(),
				p.getPublicUrl(), p.getNotes(), p.getCreatedAt(), p.getUpdatedAt(), access.memberRole(),
				new Permissions(access.canEdit(), access.canManageMembers(), access.canDelete()));
	}

	public record Permissions(boolean edit, boolean manageMembers, boolean delete) {
	}

}
