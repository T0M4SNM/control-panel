package io.github.t0m4snm.control_panel_backend.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Purga de logs de más de 7 días ({@code app.logs.retention-days}). Cada hora, porque los logs crecen
 * rápido. Se desactiva con el resto de tareas, con {@code app.monitoring.enabled=false}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class LogRetentionScheduler {

	private static final Logger log = LoggerFactory.getLogger(LogRetentionScheduler.class);

	private final LogService logs;

	public LogRetentionScheduler(LogService logs) {
		this.logs = logs;
	}

	@Scheduled(cron = "${app.logs.purge-cron:0 15 * * * *}", zone = "${app.time-zone:Europe/Madrid}")
	void purge() {
		int purged = logs.purge();
		if (purged > 0) {
			log.info("Purgadas {} líneas de log antiguas", purged);
		}
	}

}
