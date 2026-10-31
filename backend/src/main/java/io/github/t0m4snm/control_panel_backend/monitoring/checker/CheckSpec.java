package io.github.t0m4snm.control_panel_backend.monitoring.checker;

import java.time.Duration;
import java.util.UUID;

import io.github.t0m4snm.control_panel_backend.monitoring.CheckType;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheck;

/**
 * Lo necesario para ejecutar una comprobación, copiado de la entidad para poder ejecutarla fuera de
 * la transacción y en paralelo.
 */
public record CheckSpec(UUID id, CheckType type, String target, Integer port, Duration timeout) {

	public static CheckSpec of(HealthCheck check) {
		return new CheckSpec(check.getId(), check.getType(), check.getTarget(), check.getPort(),
				Duration.ofMillis(check.getTimeoutMs()));
	}

	/** {@code target} sin los corchetes de una IPv6 escrita como {@code [::1]}. */
	String host() {
		return target.startsWith("[") && target.endsWith("]") ? target.substring(1, target.length() - 1) : target;
	}

}
