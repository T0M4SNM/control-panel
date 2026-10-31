package io.github.t0m4snm.control_panel_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.t0m4snm.control_panel_backend.TestcontainersConfiguration;
import io.github.t0m4snm.control_panel_backend.user.AppUser;
import io.github.t0m4snm.control_panel_backend.user.AppUserRepository;
import io.github.t0m4snm.control_panel_backend.user.UserRole;
import io.github.t0m4snm.control_panel_backend.user.Workspace;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/**
 * Recorre con un servidor HTTP real lo mismo que hará el frontend: pedir la cookie CSRF,
 * hacer login enviando el token en la cabecera y usar la cookie de sesión.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SpaLoginFlowTests {

	private static final String EMAIL = "spa@example.com";

	private static final String PASSWORD = "una-contraseña-larga";

	@LocalServerPort
	private int port;

	@Autowired
	private WorkspaceRepository workspaces;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private final CookieManager cookies = new CookieManager();

	private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

	private Workspace workspace;

	@BeforeEach
	void setUp() {
		workspace = workspaces.save(new Workspace("SPA"));
		users.save(new AppUser(workspace, EMAIL, passwordEncoder.encode(PASSWORD), "SPA", UserRole.MEMBER));
	}

	@AfterEach
	void tearDown() {
		users.findByEmailIgnoreCase(EMAIL).ifPresent(users::delete);
		workspaces.delete(workspace);
	}

	@Test
	void loginWithCsrfHeaderOpensSession() throws Exception {
		assertThat(send(get("/api/auth/csrf")).statusCode()).isEqualTo(204);
		String csrfToken = cookie("XSRF-TOKEN");
		assertThat(csrfToken).isNotBlank();

		HttpResponse<String> login = send(loginRequest().header("X-XSRF-TOKEN", csrfToken).build());
		assertThat(login.statusCode()).isEqualTo(204);

		HttpResponse<String> me = send(get("/api/auth/me"));
		assertThat(me.statusCode()).isEqualTo(200);
		assertThat(me.body()).contains("\"email\":\"" + EMAIL + "\"").contains("\"role\":\"MEMBER\"");
	}

	@Test
	void loginWithoutCsrfHeaderIsRejected() throws Exception {
		send(get("/api/auth/csrf"));

		assertThat(send(loginRequest().build()).statusCode()).isEqualTo(403);
	}

	@Test
	void sessionCookieIsHttpOnly() throws Exception {
		send(get("/api/auth/csrf"));
		HttpResponse<String> login = send(loginRequest().header("X-XSRF-TOKEN", cookie("XSRF-TOKEN")).build());

		assertThat(login.headers().allValues("Set-Cookie"))
				.anySatisfy(header -> assertThat(header).startsWith("JSESSIONID=").contains("HttpOnly")
						.contains("SameSite=Lax"));
	}

	private HttpRequest.Builder loginRequest() {
		String form = "email=" + encode(EMAIL) + "&password=" + encode(PASSWORD);
		return HttpRequest.newBuilder(uri("/api/auth/login"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form));
	}

	private HttpRequest get(String path) {
		return HttpRequest.newBuilder(uri(path)).GET().build();
	}

	private HttpResponse<String> send(HttpRequest request) throws Exception {
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private String cookie(String name) {
		return cookies.getCookieStore().getCookies().stream()
				.filter(cookie -> cookie.getName().equals(name))
				.map(HttpCookie::getValue)
				.findFirst()
				.orElse(null);
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

}
