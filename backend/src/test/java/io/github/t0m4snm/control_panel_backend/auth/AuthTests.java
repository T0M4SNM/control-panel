package io.github.t0m4snm.control_panel_backend.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.TestcontainersConfiguration;
import io.github.t0m4snm.control_panel_backend.user.AppUser;
import io.github.t0m4snm.control_panel_backend.user.AppUserRepository;
import io.github.t0m4snm.control_panel_backend.user.UserRole;
import io.github.t0m4snm.control_panel_backend.user.Workspace;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/**
 * Reglas de login y sesión con MockMvc. El flujo real del frontend con la cookie CSRF está en
 * {@link SpaLoginFlowTests}: el {@code csrf()} de MockMvc sustituye el repositorio CSRF y aquí no
 * se puede comprobar la cookie.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthTests {

	private static final String PASSWORD = "una-contraseÃ±a-larga";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private WorkspaceRepository workspaces;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private AppUser admin;

	@BeforeEach
	void setUp() {
		Workspace workspace = workspaces.save(new Workspace("Pruebas"));
		admin = users.save(new AppUser(workspace, "admin@example.com", passwordEncoder.encode(PASSWORD),
				"Admin", UserRole.ADMIN));
	}

	@Test
	void healthIsPublic() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void apiRequiresSession() throws Exception {
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void loginWithValidCredentialsOpensSession() throws Exception {
		MockHttpSession session = login("admin@example.com", PASSWORD);

		mvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(admin.getId().toString()))
				.andExpect(jsonPath("$.email").value("admin@example.com"))
				.andExpect(jsonPath("$.role").value("ADMIN"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void loginIgnoresEmailCase() throws Exception {
		mvc.perform(formLogin("/api/auth/login").userParameter("email").user("Admin@Example.com").password(PASSWORD))
				.andExpect(status().isNoContent());
	}

	@Test
	void loginWithWrongPasswordFails() throws Exception {
		mvc.perform(formLogin("/api/auth/login").userParameter("email").user("admin@example.com").password("mala"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void loginWithUnknownUserFails() throws Exception {
		mvc.perform(formLogin("/api/auth/login").userParameter("email").user("nadie@example.com").password(PASSWORD))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void inactiveUserCannotLogin() throws Exception {
		admin.setActive(false);
		users.saveAndFlush(admin);

		mvc.perform(formLogin("/api/auth/login").userParameter("email").user("admin@example.com").password(PASSWORD))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void loginWithoutCsrfTokenIsRejected() throws Exception {
		mvc.perform(post("/api/auth/login").param("email", "admin@example.com").param("password", PASSWORD))
				.andExpect(status().isForbidden());
	}

	@Test
	void logoutClosesSession() throws Exception {
		MockHttpSession session = login("admin@example.com", PASSWORD);

		mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isUnauthorized());
	}

	private MockHttpSession login(String email, String password) throws Exception {
		return (MockHttpSession) mvc
				.perform(formLogin("/api/auth/login").userParameter("email").user(email).password(password))
				.andExpect(status().isNoContent())
				.andReturn().getRequest().getSession(false);
	}

}
