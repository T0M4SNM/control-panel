package io.github.t0m4snm.control_panel_backend.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.sun.net.httpserver.HttpServer;

import io.github.t0m4snm.control_panel_backend.TestcontainersConfiguration;
import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheckResponse.Result;
import io.github.t0m4snm.control_panel_backend.user.UserRole;
import io.github.t0m4snm.control_panel_backend.user.Workspace;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/**
 * Ejecución real de comprobaciones contra un servidor HTTP local cuya respuesta controla el test.
 * Sin {@code @Transactional}: el ejecutor guarda cada resultado en su propia transacción, así que
 * los datos se crean y se borran a mano.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class MonitoringServiceTests {

	@Autowired
	private MonitoringService monitoring;

	@Autowired
	private WorkspaceRepository workspaces;

	@Autowired
	private HealthCheckRepository checks;

	@Autowired
	private CheckResultRepository results;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ApplicationEvents events;

	private final AtomicInteger responseStatus = new AtomicInteger(200);

	private HttpServer http;

	private Workspace workspace;

	private AuthenticatedUser admin;

	@BeforeEach
	void setUp() throws IOException {
		http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		http.createContext("/", exchange -> {
			exchange.sendResponseHeaders(responseStatus.get(), -1);
			exchange.close();
		});
		http.start();
		workspace = workspaces.save(new Workspace("Monitorización"));
		admin = new AuthenticatedUser(UUID.randomUUID(), workspace.getId(), "admin@example.com", "Admin",
				UserRole.ADMIN, "x", true);
	}

	@AfterEach
	void tearDown() {
		http.stop(0);
		// check_result y notification se borran en cascada
		jdbc.update("DELETE FROM health_check WHERE workspace_id = ?", workspace.getId());
		jdbc.update("DELETE FROM workspace WHERE id = ?", workspace.getId());
	}

	@Test
	void goesDownAfterThresholdAndBackUpPublishingEvents() {
		HealthCheck check = newHttpCheck("Web", 2, true);

		responseStatus.set(503);
		Result first = monitoring.runNow(admin, check.getId());
		assertThat(first.success()).isFalse();
		assertThat(first.statusCode()).isEqualTo(503);
		assertThat(statusOf(check)).isEqualTo(CheckStatus.UNKNOWN);

		monitoring.runNow(admin, check.getId());
		assertThat(statusOf(check)).isEqualTo(CheckStatus.DOWN);

		responseStatus.set(200);
		monitoring.runNow(admin, check.getId());
		assertThat(statusOf(check)).isEqualTo(CheckStatus.UP);

		assertThat(events.stream(HealthCheckStatusChanged.class))
				.extracting(HealthCheckStatusChanged::previous, HealthCheckStatusChanged::current)
				.containsExactly(
						tuple(CheckStatus.UNKNOWN, CheckStatus.DOWN),
						tuple(CheckStatus.DOWN, CheckStatus.UP));
		assertThat(results.findByHealthCheckIdOrderByCheckedAtDesc(check.getId(), Limit.of(10))).hasSize(3);
	}

	@Test
	void runsOnlyEnabledChecksThatAreDue() {
		HealthCheck due = newHttpCheck("Pendiente", 3, true);
		HealthCheck disabled = newHttpCheck("Desactivada", 3, false);
		HealthCheck recent = newHttpCheck("Reciente", 3, true);
		monitoring.runNow(admin, recent.getId());

		monitoring.runDueChecks();

		assertThat(statusOf(due)).isEqualTo(CheckStatus.UP);
		assertThat(statusOf(disabled)).isEqualTo(CheckStatus.UNKNOWN);
		// La reciente se comprobó hace un momento y su intervalo es de 5 min: no le toca otra vez
		assertThat(results.findByHealthCheckIdOrderByCheckedAtDesc(recent.getId(), Limit.of(10))).hasSize(1);
	}

	@Test
	void purgesResultsOlderThan30Days() {
		HealthCheck check = newHttpCheck("Web", 3, true);
		Instant now = Instant.now();
		results.save(new CheckResult(check, now.minus(31, ChronoUnit.DAYS), true, 10, 200, null, null));
		results.save(new CheckResult(check, now.minus(29, ChronoUnit.DAYS), true, 10, 200, null, null));

		assertThat(monitoring.purgeOldResults()).isGreaterThanOrEqualTo(1);
		assertThat(results.findByHealthCheckIdOrderByCheckedAtDesc(check.getId(), Limit.of(10))).hasSize(1);
	}

	private HealthCheck newHttpCheck(String name, int threshold, boolean enabled) {
		HealthCheck check = new HealthCheck(workspace);
		check.setName(name);
		check.setType(CheckType.HTTP);
		check.setTarget("http://127.0.0.1:" + http.getAddress().getPort() + "/");
		check.setTimeoutMs(2_000);
		check.setFailureThreshold(threshold);
		check.setEnabled(enabled);
		return checks.save(check);
	}

	private CheckStatus statusOf(HealthCheck check) {
		return checks.findById(check.getId()).orElseThrow().getCurrentStatus();
	}

}
