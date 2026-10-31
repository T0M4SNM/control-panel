package io.github.t0m4snm.control_panel_backend.project;

/**
 * Lo que un usuario puede hacer con un proyecto que ya puede ver.
 *
 * @param memberRole su rol en el proyecto, o {@code null} si es un admin que no es miembro
 */
record ProjectAccess(Project project, ProjectRole memberRole, boolean admin) {

	boolean canEdit() {
		return admin || memberRole.canEdit();
	}

	boolean canManageMembers() {
		return admin || memberRole.canManageMembers();
	}

	boolean canDelete() {
		return admin;
	}

}
