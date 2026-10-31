package io.github.t0m4snm.control_panel_backend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Revisa los vencimientos cada mañana y también al arrancar, por si el servidor estaba apagado a esa
 * hora. Como los avisos no se repiten, revisar de más no molesta. Se desactiva con
 * {@code app.notifications.enabled=false}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationScheduler {

	private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

	private final ExpiryNotifier expiryNotifier;

	public NotificationScheduler(ExpiryNotifier expiryNotifier) {
		this.expiryNotifier = expiryNotifier;
	}

	@EventListener(ApplicationReadyEvent.class)
	void onStartup() {
		checkExpiries();
	}

	/** Cada día a las 8:00 en Madrid. */
	@Scheduled(cron = "${app.notifications.expiry-cron:0 0 8 * * *}", zone = "${app.time-zone:Europe/Madrid}")
	void checkExpiries() {
		int created = expiryNotifier.run();
		if (created > 0) {
			log.info("Creados {} avisos de vencimiento", created);
		}
	}

}
