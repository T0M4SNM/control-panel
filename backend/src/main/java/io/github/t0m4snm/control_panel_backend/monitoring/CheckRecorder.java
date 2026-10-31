package io.github.t0m4snm.control_panel_backend.monitoring;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.monitoring.checker.CheckOutcome;

/**
 * Guarda el resultado de una ejecución y actualiza el estado, cada uno en su propia transacción: si
 * falla al guardar una comprobación, las demás no se pierden.
 */
@Component
class CheckRecorder {

	private final HealthCheckRepository checks;

	private final CheckResultRepository results;

	private final ApplicationEventPublisher events;

	CheckRecorder(HealthCheckRepository checks, CheckResultRepository results, ApplicationEventPublisher events) {
		this.checks = checks;
		this.results = results;
		this.events = events;
	}

	@Transactional
	public CheckResult record(UUID checkId, CheckOutcome outcome, Instant checkedAt) {
		// Puede haberse borrado mientras se ejecutaba
		HealthCheck check = checks.findById(checkId).orElse(null);
		if (check == null) {
			return null;
		}
		CheckResult result = results.save(new CheckResult(check, checkedAt, outcome.success(), outcome.responseMs(),
				outcome.statusCode(), outcome.error(), outcome.sslExpiresAt()));
		check.record(outcome.success(), checkedAt).ifPresent(previous -> events.publishEvent(
				new HealthCheckStatusChanged(checkId, previous, check.getCurrentStatus(), checkedAt)));
		return result;
	}

}
