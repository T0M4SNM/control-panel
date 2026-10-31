package io.github.t0m4snm.control_panel_backend.monitoring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheckResponse.Result;
import io.github.t0m4snm.control_panel_backend.monitoring.checker.CheckOutcome;
import io.github.t0m4snm.control_panel_backend.monitoring.checker.CheckSpec;
import io.github.t0m4snm.control_panel_backend.monitoring.checker.Checker;

/**
 * Ejecuta las comprobaciones. Las de red se lanzan en paralelo con hilos virtuales (Java 21): una
 * web lenta no retrasa a las demás y cada hilo apenas gasta memoria, importante con 2 GB de RAM.
 */
@Service
public class MonitoringService {

	static final Duration RESULT_RETENTION = Duration.ofDays(30);

	private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

	private final HealthCheckRepository checks;

	private final CheckResultRepository results;

	private final CheckRecorder recorder;

	private final Map<CheckType, Checker> checkers = new EnumMap<>(CheckType.class);

	private final TransactionTemplate readOnly;

	private final TransactionTemplate readWrite;

	private final Clock clock;

	MonitoringService(HealthCheckRepository checks, CheckResultRepository results, CheckRecorder recorder,
			List<Checker> checkers, PlatformTransactionManager transactionManager, Clock clock) {
		this.checks = checks;
		this.results = results;
		this.recorder = recorder;
		checkers.forEach(checker -> this.checkers.put(checker.type(), checker));
		this.readOnly = new TransactionTemplate(transactionManager);
		this.readOnly.setReadOnly(true);
		this.readWrite = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** Ejecuta las comprobaciones activas a las que les toca; devuelve cuántas se han ejecutado. */
	public int runDueChecks() {
		Instant now = clock.instant();
		List<CheckSpec> due = readOnly.execute(status -> checks.findByEnabledTrue().stream()
				.filter(check -> check.getLastCheckedAt() == null
						|| !check.getLastCheckedAt().plusSeconds(check.getIntervalSeconds()).isAfter(now))
				.map(CheckSpec::of)
				.toList());
		if (due.isEmpty()) {
			return 0;
		}

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<CheckOutcome>> outcomes = due.stream()
					.map(spec -> executor.submit(() -> execute(spec)))
					.toList();
			for (int i = 0; i < due.size(); i++) {
				CheckOutcome outcome;
				try {
					outcome = outcomes.get(i).get();
				} catch (Exception e) {
					outcome = CheckOutcome.failure(0, CheckOutcome.describe(e));
				}
				try {
					recorder.record(due.get(i).id(), outcome, clock.instant());
				} catch (RuntimeException e) {
					log.error("No se pudo guardar el resultado de la comprobación {}", due.get(i).id(), e);
				}
			}
		}
		return due.size();
	}

	/** Ejecuta una comprobación ahora mismo, sin esperar a su turno. Solo admin. */
	public Result runNow(AuthenticatedUser user, UUID checkId) {
		if (!user.isAdmin()) {
			throw ApiErrors.forbidden();
		}
		CheckSpec spec = readOnly.execute(status -> checks.findByIdAndWorkspaceId(checkId, user.workspaceId())
				.map(CheckSpec::of)
				.orElseThrow(ApiErrors::notFound));
		CheckResult result = recorder.record(checkId, execute(spec), clock.instant());
		if (result == null) {
			// Se borró mientras se ejecutaba
			throw ApiErrors.notFound();
		}
		return Result.from(result);
	}

	/** Borra los resultados de más de 30 días; devuelve cuántos. */
	public int purgeOldResults() {
		Instant before = clock.instant().minus(RESULT_RETENTION);
		Integer deleted = readWrite.execute(status -> results.deleteCheckedBefore(before));
		return deleted == null ? 0 : deleted;
	}

	private CheckOutcome execute(CheckSpec spec) {
		Checker checker = checkers.get(spec.type());
		if (checker == null) {
			return CheckOutcome.failure(0, "Tipo de comprobación sin implementar: " + spec.type());
		}
		return checker.check(spec);
	}

}
