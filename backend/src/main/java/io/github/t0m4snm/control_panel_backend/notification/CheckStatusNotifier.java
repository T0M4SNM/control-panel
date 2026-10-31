package io.github.t0m4snm.control_panel_backend.notification;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import io.github.t0m4snm.control_panel_backend.monitoring.CheckResult;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckResultRepository;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckStatus;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheck;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheckRepository;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheckStatusChanged;

/**
 * Avisa cuando una comprobación cae ({@code DOWN}) y cuando se recupera tras una caída. Se ejecuta
 * dentro de la misma transacción que guarda el resultado: o se guardan las dos cosas o ninguna.
 * Pasar de {@code UNKNOWN} a {@code UP} no avisa: es la primera comprobación con éxito.
 */
@Component
class CheckStatusNotifier {

	private final HealthCheckRepository checks;

	private final CheckResultRepository results;

	private final NotificationRecipients recipients;

	private final NotificationWriter writer;

	CheckStatusNotifier(HealthCheckRepository checks, CheckResultRepository results,
			NotificationRecipients recipients, NotificationWriter writer) {
		this.checks = checks;
		this.results = results;
		this.recipients = recipients;
		this.writer = writer;
	}

	@EventListener
	void onStatusChanged(HealthCheckStatusChanged event) {
		boolean down = event.current() == CheckStatus.DOWN;
		boolean recovered = event.current() == CheckStatus.UP && event.previous() == CheckStatus.DOWN;
		if (!down && !recovered) {
			return;
		}
		HealthCheck check = checks.findById(event.healthCheckId()).orElse(null);
		if (check == null) {
			return;
		}

		NotificationDraft draft;
		long changedAt = event.changedAt().getEpochSecond();
		if (down) {
			String error = results.findByHealthCheckIdOrderByCheckedAtDesc(check.getId(), Limit.of(1)).stream()
					.map(CheckResult::getError)
					.filter(e -> e != null)
					.findFirst()
					.map(e -> " Último error: " + e)
					.orElse("");
			draft = new NotificationDraft(NotificationType.CHECK_DOWN, Severity.CRITICAL, "Caída: " + check.getName(),
					"%s (%s) no responde tras %d intentos seguidos.%s".formatted(check.getName(), check.getTarget(),
							check.getConsecutiveFailures(), error),
					null, check.getId(), "check:%s:down:%d".formatted(check.getId(), changedAt));
		} else {
			draft = new NotificationDraft(NotificationType.CHECK_RECOVERED, Severity.INFO,
					"Recuperada: " + check.getName(),
					"%s (%s) vuelve a responder.".formatted(check.getName(), check.getTarget()),
					null, check.getId(), "check:%s:up:%d".formatted(check.getId(), changedAt));
		}
		writer.notify(recipients.forHealthCheck(check), draft);
	}

}
