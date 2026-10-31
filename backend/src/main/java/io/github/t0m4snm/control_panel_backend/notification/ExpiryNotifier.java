package io.github.t0m4snm.control_panel_backend.notification;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.contract.BillingPeriod;
import io.github.t0m4snm.control_panel_backend.contract.Contract;
import io.github.t0m4snm.control_panel_backend.contract.ContractRepository;
import io.github.t0m4snm.control_panel_backend.contract.ContractStatus;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckResult;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckResultRepository;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckType;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheck;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheckRepository;

/**
 * Avisos de vencimiento a 30, 15 y 7 días (umbrales globales de la aplicación):
 * <ul>
 * <li>Contratos activos, por su {@code renewal_date}. Solo a los {@code ADMIN}.</li>
 * <li>Certificados SSL, por la caducidad del último resultado con éxito de cada comprobación
 * {@code SSL}. A los admin y a los miembros de los proyectos afectados.</li>
 * </ul>
 * Se manda solo el umbral más cercano ya alcanzado: si la tarea no se ejecutó unos días, a 5 días del
 * vencimiento llega el aviso de 7, no los tres a la vez. Ejecutarla varias veces no repite avisos.
 */
@Service
public class ExpiryNotifier {

	static final List<Integer> THRESHOLD_DAYS = List.of(7, 15, 30);

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final ContractRepository contracts;

	private final HealthCheckRepository checks;

	private final CheckResultRepository results;

	private final NotificationRecipients recipients;

	private final NotificationWriter writer;

	private final Clock clock;

	public ExpiryNotifier(ContractRepository contracts, HealthCheckRepository checks, CheckResultRepository results,
			NotificationRecipients recipients, NotificationWriter writer, Clock clock) {
		this.contracts = contracts;
		this.checks = checks;
		this.results = results;
		this.recipients = recipients;
		this.writer = writer;
		this.clock = clock;
	}

	/** Revisa contratos y certificados; devuelve cuántos avisos ha creado. */
	@Transactional
	public int run() {
		LocalDate today = LocalDate.now(clock);
		return notifyContracts(today) + notifySslCertificates(today);
	}

	private int notifyContracts(LocalDate today) {
		int created = 0;
		for (Contract contract : contracts.findByStatus(ContractStatus.ACTIVE)) {
			long daysLeft = ChronoUnit.DAYS.between(today, contract.getRenewalDate());
			OptionalInt threshold = thresholdFor(daysLeft);
			if (threshold.isEmpty()) {
				continue;
			}
			int t = threshold.getAsInt();
			String message = "El contrato «%s» (%s) vence el %s, %s. Importe: %s %s. Renovación automática: %s."
					.formatted(contract.getName(), contract.getProvider(), contract.getRenewalDate().format(DATE),
							inDays(daysLeft), euros(contract), contract.getBillingPeriod() == BillingPeriod.MONTHLY
									? "al mes" : "al año",
							contract.isAutoRenew() ? "sí" : "no");
			NotificationDraft draft = new NotificationDraft(NotificationType.CONTRACT_EXPIRING, severityFor(t),
					"Vence pronto: " + contract.getName(), message, contract.getId(), null,
					"contract:%s:%dd:%s".formatted(contract.getId(), t, contract.getRenewalDate()));
			created += writer.notify(recipients.admins(contract.getWorkspace().getId()), draft).size();
		}
		return created;
	}

	private int notifySslCertificates(LocalDate today) {
		int created = 0;
		for (HealthCheck check : checks.findByEnabledTrueAndType(CheckType.SSL)) {
			Optional<CheckResult> latest = results
					.findFirstByHealthCheckIdAndSslExpiresAtIsNotNullOrderByCheckedAtDesc(check.getId());
			if (latest.isEmpty()) {
				continue;
			}
			LocalDate expires = LocalDate.ofInstant(latest.get().getSslExpiresAt(), clock.getZone());
			long daysLeft = ChronoUnit.DAYS.between(today, expires);
			OptionalInt threshold = thresholdFor(daysLeft);
			if (threshold.isEmpty()) {
				continue;
			}
			int t = threshold.getAsInt();
			NotificationDraft draft = new NotificationDraft(NotificationType.SSL_EXPIRING, severityFor(t),
					"Certificado SSL por caducar: " + check.getName(),
					"El certificado de %s caduca el %s, %s.".formatted(check.getTarget(), expires.format(DATE),
							inDays(daysLeft)),
					null, check.getId(), "ssl:%s:%dd:%s".formatted(check.getId(), t, expires));
			created += writer.notify(recipients.forHealthCheck(check), draft).size();
		}
		return created;
	}

	/** El umbral más pequeño que ya se ha alcanzado, o vacío si falta más de 30 días o ya venció. */
	static OptionalInt thresholdFor(long daysLeft) {
		if (daysLeft < 0) {
			return OptionalInt.empty();
		}
		return THRESHOLD_DAYS.stream().mapToInt(Integer::intValue).filter(t -> daysLeft <= t).findFirst();
	}

	static Severity severityFor(int threshold) {
		return switch (threshold) {
			case 7 -> Severity.CRITICAL;
			case 15 -> Severity.WARNING;
			default -> Severity.INFO;
		};
	}

	private static String inDays(long daysLeft) {
		if (daysLeft == 0) {
			return "hoy";
		}
		return daysLeft == 1 ? "mañana" : "dentro de " + daysLeft + " días";
	}

	private static String euros(Contract contract) {
		return NumberFormat.getCurrencyInstance(Locale.of("es", "ES")).format(contract.getAmount());
	}

}
