package io.github.t0m4snm.control_panel_backend.monitoring;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos de una comprobación para crear o reemplazar ({@code PUT}). Los campos numéricos y
 * {@code enabled} toman sus valores por defecto si no llegan: cada 5 minutos, 10 s de espera,
 * alerta tras 3 fallos seguidos, activada.
 */
public record HealthCheckRequest(
		@NotBlank @Size(max = 150) String name,
		@NotNull CheckType type,
		@NotBlank @Size(max = 500) String target,
		@Min(1) @Max(65535) Integer port,
		UUID serverId,
		UUID domainId,
		UUID projectId,
		@Min(60) @Max(86_400) Integer intervalSeconds,
		@Min(1_000) @Max(30_000) Integer timeoutMs,
		@Min(1) @Max(10) Integer failureThreshold,
		Boolean enabled) {
}
