package io.github.t0m4snm.control_panel_backend.apperror;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Purga nocturna de las ocurrencias de errores de más de 30 días. Va con el resto de tareas de
 * vigilancia: se desactiva igual, con {@code app.monitoring.enabled=false}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class ErrorRetentionScheduler {

	private static final Logger log = LoggerFactory.getLogger(ErrorRetentionScheduler.class);

	private final ErrorService errors;

	public ErrorRetentionScheduler(ErrorService errors) {
		this.errors = errors;
	}

	/** Cada noche, a las 3:45 en Madrid. */
	@Scheduled(cron = "${app.errors.purge-cron:0 45 3 * * *}", zone = "${app.time-zone:Europe/Madrid}")
	void purgeOldEvents() {
		log.info("Purgadas {} ocurrencias de errores de más de 30 días", errors.purgeOldEvents());
	}

}
