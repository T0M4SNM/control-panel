package io.github.t0m4snm.control_panel_backend.user;

/**
 * Rol del usuario dentro de su workspace.
 * {@code ADMIN} lo ve todo, incluidas las finanzas; {@code MEMBER} solo sus proyectos.
 */
public enum UserRole {
	ADMIN,
	MEMBER
}
