package io.github.t0m4snm.control_panel_backend.visit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Purga nocturna de visitas de más de 90 días y de las sales de días pasados. Se desactiva con el resto
 * de tareas, con {@code app.monitoring.enabled=false}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class VisitRetentionScheduler {

	private static final Logger log = LoggerFactory.getLogger(VisitRetentionScheduler.class);

	private final VisitService visits;

	public VisitRetentionScheduler(VisitService visits) {
		this.visits = visits;
	}

	/** Cada noche, a las 0:05 en Madrid: justo después de cambiar de día, para tirar la sal de ayer. */
	@Scheduled(cron = "${app.visits.purge-cron:0 5 0 * * *}", zone = "${app.time-zone:Europe/Madrid}")
	void purge() {
		log.info("Purgadas {} visitas de más de 90 días", visits.purge());
	}

}
