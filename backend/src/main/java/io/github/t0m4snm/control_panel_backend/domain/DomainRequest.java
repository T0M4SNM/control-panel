package io.github.t0m4snm.control_panel_backend.domain;

import java.util.Locale;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos de un dominio para crear o reemplazar ({@code PUT}). {@code name} es el nombre completo
 * (p. ej. {@code api.ejemplo.com}); si hay {@code parentId}, debe terminar en el nombre del padre.
 */
public record DomainRequest(
		@NotBlank @Size(max = 253) @Pattern(regexp = HOSTNAME, message = "no es un nombre de dominio válido") String name,
		UUID parentId,
		UUID contractId,
		UUID serverId,
		UUID projectId,
		@Size(max = 100) String registrar,
		String notes) {

	/** Etiquetas de letras, números y guiones separadas por puntos, con al menos un punto. */
	static final String HOSTNAME = "(?i)^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z][a-z0-9-]{0,61}[a-z0-9]$";

	String normalizedName() {
		return name.trim().toLowerCase(Locale.ROOT);
	}

}
