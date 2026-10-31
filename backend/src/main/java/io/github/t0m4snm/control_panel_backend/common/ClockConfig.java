package io.github.t0m4snm.control_panel_backend.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicación en la zona horaria del usuario, para que «este mes» o «hoy» no dependan
 * de la zona del servidor. Inyectarlo en lugar de llamar a {@code now()} facilita los tests.
 */
@Configuration
public class ClockConfig {

	@Bean
	Clock clock(@Value("${app.time-zone:Europe/Madrid}") ZoneId zone) {
		return Clock.system(zone);
	}

}
