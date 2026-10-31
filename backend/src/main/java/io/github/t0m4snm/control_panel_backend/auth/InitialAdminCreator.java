package io.github.t0m4snm.control_panel_backend.auth;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.user.AppUser;
import io.github.t0m4snm.control_panel_backend.user.AppUserRepository;
import io.github.t0m4snm.control_panel_backend.user.UserRole;
import io.github.t0m4snm.control_panel_backend.user.Workspace;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/**
 * Al arrancar, si no hay ningún usuario, crea el workspace y el primer administrador con los
 * datos de {@link BootstrapProperties}. Si ya hay usuarios no hace nada, así que se puede dejar
 * configurado sin riesgo de que sobrescriba nada.
 */
@Component
@EnableConfigurationProperties(BootstrapProperties.class)
public class InitialAdminCreator implements ApplicationRunner {

	static final int MIN_PASSWORD_LENGTH = 12;

	private static final Logger log = LoggerFactory.getLogger(InitialAdminCreator.class);

	private final BootstrapProperties properties;

	private final WorkspaceRepository workspaces;

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	public InitialAdminCreator(BootstrapProperties properties, WorkspaceRepository workspaces, AppUserRepository users,
			PasswordEncoder passwordEncoder) {
		this.properties = properties;
		this.workspaces = workspaces;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (users.count() > 0) {
			return;
		}
		if (!properties.hasAdminCredentials()) {
			log.warn("No hay usuarios. Define APP_BOOTSTRAP_ADMIN_EMAIL y APP_BOOTSTRAP_ADMIN_PASSWORD "
					+ "para crear el primer administrador.");
			return;
		}
		if (properties.adminPassword().length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalStateException(
					"APP_BOOTSTRAP_ADMIN_PASSWORD debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
		}

		Workspace workspace = workspaces.save(new Workspace(orDefault(properties.workspaceName(), "Principal")));
		String email = properties.adminEmail().trim().toLowerCase(Locale.ROOT);
		users.save(new AppUser(workspace, email, passwordEncoder.encode(properties.adminPassword()),
				orDefault(properties.adminName(), "Administrador"), UserRole.ADMIN));
		log.info("Creado el primer administrador {} en el workspace '{}'", email, workspace.getName());
	}

	private static String orDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

}
