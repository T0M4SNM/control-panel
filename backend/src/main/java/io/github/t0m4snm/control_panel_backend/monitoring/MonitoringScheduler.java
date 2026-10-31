package io.github.t0m4snm.control_panel_backend.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Tareas programadas de la monitorización. Se pueden desactivar con
 * {@code app.monitoring.enabled=false} (p. ej. en desarrollo).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringScheduler {

	private static final Logger log = LoggerFactory.getLogger(MonitoringScheduler.class);

	private final MonitoringService monitoring;

	public MonitoringScheduler(MonitoringService monitoring) {
		this.monitoring = monitoring;
	}

	/**
	 * Cada 30 s mira qué comprobaciones tocan según su intervalo. Con el intervalo por defecto
	 * (5 min) cada una se ejecuta como mucho 30 s tarde.
	 */
	@Scheduled(fixedDelayString = "${app.monitoring.tick:30s}", initialDelayString = "${app.monitoring.tick:30s}")
	void runDueChecks() {
		int executed = monitoring.runDueChecks();
		if (executed > 0) {
			log.debug("Ejecutadas {} comprobaciones", executed);
		}
	}

	/** Cada noche, a las 3:30 en Madrid. */
	@Scheduled(cron = "${app.monitoring.purge-cron:0 30 3 * * *}", zone = "${app.time-zone:Europe/Madrid}")
	void purgeOldResults() {
		log.info("Purgados {} resultados de comprobaciones de más de 30 días", monitoring.purgeOldResults());
	}

}
