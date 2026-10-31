package io.github.t0m4snm.control_panel_backend.server;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Datos de un servidor para crear o reemplazar ({@code PUT}). Nunca incluye contraseñas. */
public record ServerRequest(
		@NotBlank @Size(max = 150) String name,
		@Size(max = 100) String provider,
		@Size(max = 45) String ipAddress,
		@Size(max = 100) String os,
		@Positive Integer cpuCores,
		@Positive Integer ramMb,
		@Positive Integer diskGb,
		String notes,
		UUID contractId) {

	void applyTo(Server server) {
		server.setName(name.trim());
		server.setProvider(blankToNull(provider));
		server.setIpAddress(blankToNull(ipAddress));
		server.setOs(blankToNull(os));
		server.setCpuCores(cpuCores);
		server.setRamMb(ramMb);
		server.setDiskGb(diskGb);
		server.setNotes(blankToNull(notes));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
