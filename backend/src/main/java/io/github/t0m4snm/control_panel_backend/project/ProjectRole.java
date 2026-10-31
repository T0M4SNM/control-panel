package io.github.t0m4snm.control_panel_backend.project;

/**
 * Rol de un usuario dentro de un proyecto.
 * {@code OWNER} edita y gestiona miembros; {@code EDITOR} edita; {@code VIEWER} solo consulta.
 */
public enum ProjectRole {
	OWNER,
	EDITOR,
	VIEWER;

	boolean canEdit() {
		return this == OWNER || this == EDITOR;
	}

	boolean canManageMembers() {
		return this == OWNER;
	}
}
