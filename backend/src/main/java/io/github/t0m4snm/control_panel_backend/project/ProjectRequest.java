package io.github.t0m4snm.control_panel_backend.project;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos editables de un proyecto. Se usa para crear y para reemplazar ({@code PUT}): los campos
 * opcionales que lleguen vacíos se borran. Si {@code status} no llega, al crear es {@code IDEA}
 * y al editar se mantiene.
 */
public record ProjectRequest(
		@NotBlank @Size(max = 150) String name,
		String description,
		ProjectStatus status,
		@URL @Size(max = 500) String repoUrl,
		@URL @Size(max = 500) String publicUrl,
		String notes) {

	void applyTo(Project project) {
		project.setDescription(blankToNull(description));
		if (status != null) {
			project.setStatus(status);
		}
		project.setRepoUrl(blankToNull(repoUrl));
		project.setPublicUrl(blankToNull(publicUrl));
		project.setNotes(blankToNull(notes));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
