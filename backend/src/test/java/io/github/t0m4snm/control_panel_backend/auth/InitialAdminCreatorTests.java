package io.github.t0m4snm.control_panel_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.TestcontainersConfiguration;
import io.github.t0m4snm.control_panel_backend.user.AppUser;
import io.github.t0m4snm.control_panel_backend.user.AppUserRepository;
import io.github.t0m4snm.control_panel_backend.user.UserRole;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InitialAdminCreatorTests {

	@Autowired
	private WorkspaceRepository workspaces;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void createsWorkspaceAndAdminWhenThereAreNoUsers() {
		creator(new BootstrapProperties("Mi workspace", " Tomas@Example.com ", "contraseña-segura", "Tomás")).run(null);

		AppUser admin = users.findByEmailIgnoreCase("tomas@example.com").orElseThrow();
		assertThat(admin.getEmail()).isEqualTo("tomas@example.com");
		assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(admin.getDisplayName()).isEqualTo("Tomás");
		assertThat(admin.getWorkspace().getName()).isEqualTo("Mi workspace");
		assertThat(passwordEncoder.matches("contraseña-segura", admin.getPasswordHash())).isTrue();
	}

	@Test
	void doesNothingWhenUsersAlreadyExist() {
		creator(new BootstrapProperties(null, "primero@example.com", "contraseña-segura", null)).run(null);
		creator(new BootstrapProperties(null, "segundo@example.com", "contraseña-segura", null)).run(null);

		assertThat(users.count()).isEqualTo(1);
		assertThat(workspaces.count()).isEqualTo(1);
	}

	@Test
	void doesNothingWithoutCredentials() {
		creator(new BootstrapProperties(null, null, null, null)).run(null);

		assertThat(users.count()).isZero();
	}

	@Test
	void rejectsShortPassword() {
		InitialAdminCreator creator = creator(new BootstrapProperties(null, "admin@example.com", "corta", null));

		assertThatThrownBy(() -> creator.run(null)).isInstanceOf(IllegalStateException.class);
		assertThat(users.count()).isZero();
	}

	private InitialAdminCreator creator(BootstrapProperties properties) {
		return new InitialAdminCreator(properties, workspaces, users, passwordEncoder);
	}

}
