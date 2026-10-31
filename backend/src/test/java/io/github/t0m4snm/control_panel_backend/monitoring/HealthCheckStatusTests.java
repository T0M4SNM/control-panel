package io.github.t0m4snm.control_panel_backend.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** Transiciones de estado de una comprobación, sin base de datos. */
class HealthCheckStatusTests {

	private static final Instant T0 = Instant.parse("2026-10-31T10:00:00Z");

	@Test
	void goesDownOnlyAfterThresholdConsecutiveFailures() {
		HealthCheck check = new HealthCheck(null);

		assertThat(check.record(false, T0)).isEmpty();
		assertThat(check.record(false, T0.plusSeconds(300))).isEmpty();
		assertThat(check.getCurrentStatus()).isEqualTo(CheckStatus.UNKNOWN);
		assertThat(check.getConsecutiveFailures()).isEqualTo(2);

		assertThat(check.record(false, T0.plusSeconds(600))).contains(CheckStatus.UNKNOWN);
		assertThat(check.getCurrentStatus()).isEqualTo(CheckStatus.DOWN);
		assertThat(check.getStatusChangedAt()).isEqualTo(T0.plusSeconds(600));

		// Seguir fallando no vuelve a contar como cambio
		assertThat(check.record(false, T0.plusSeconds(900))).isEmpty();
	}

	@Test
	void recoversWithFirstSuccess() {
		HealthCheck check = new HealthCheck(null);
		check.record(true, T0);
		assertThat(check.getCurrentStatus()).isEqualTo(CheckStatus.UP);

		check.record(false, T0.plusSeconds(300));
		check.record(false, T0.plusSeconds(600));
		assertThat(check.getCurrentStatus()).isEqualTo(CheckStatus.UP);
		check.record(false, T0.plusSeconds(900));
		assertThat(check.getCurrentStatus()).isEqualTo(CheckStatus.DOWN);

		assertThat(check.record(true, T0.plusSeconds(1200))).contains(CheckStatus.DOWN);
		assertThat(check.getCurrentStatus()).isEqualTo(CheckStatus.UP);
		assertThat(check.getConsecutiveFailures()).isZero();
	}

	@Test
	void aSuccessResetsTheFailureCount() {
		HealthCheck check = new HealthCheck(null);
		check.record(false, T0);
		check.record(false, T0.plusSeconds(300));
		check.record(true, T0.plusSeconds(600));
		check.record(false, T0.plusSeconds(900));
		check.record(false, T0.plusSeconds(1200));

		assertThat(check.getCurrentStatus()).isEqualTo(CheckStatus.UP);
	}

}
