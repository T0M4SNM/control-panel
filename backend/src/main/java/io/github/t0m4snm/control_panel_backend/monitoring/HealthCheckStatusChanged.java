package io.github.t0m4snm.control_panel_backend.monitoring;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de Spring que se publica cuando una comprobación cambia de estado (p. ej. de {@code UP} a
 * {@code DOWN}). Los avisos lo escuchan para notificar caídas y recuperaciones.
 */
public record HealthCheckStatusChanged(UUID healthCheckId, CheckStatus previous, CheckStatus current,
		Instant changedAt) {
}
