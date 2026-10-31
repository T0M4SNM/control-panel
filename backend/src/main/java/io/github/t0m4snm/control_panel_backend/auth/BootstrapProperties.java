package io.github.t0m4snm.control_panel_backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos del primer administrador, leídos de variables de entorno (ver {@code .env.example}).
 * Solo se usan si la base de datos aún no tiene ningún usuario.
 */
@ConfigurationProperties("app.bootstrap")
public record BootstrapProperties(String workspaceName, String adminEmail, String adminPassword, String adminName) {

	boolean hasAdminCredentials() {
		return adminEmail != null && !adminEmail.isBlank() && adminPassword != null && !adminPassword.isBlank();
	}

}
