package io.github.t0m4snm.control_panel_backend.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.github.t0m4snm.control_panel_backend.TestcontainersConfiguration;
import io.github.t0m4snm.control_panel_backend.project.Project;
import io.github.t0m4snm.control_panel_backend.project.ProjectRepository;
import io.github.t0m4snm.control_panel_backend.server.Server;
import io.github.t0m4snm.control_panel_backend.server.ServerRepository;
import io.github.t0m4snm.control_panel_backend.user.AppUser;
import io.github.t0m4snm.control_panel_backend.user.AppUserRepository;
import io.github.t0m4snm.control_panel_backend.user.UserRole;
import io.github.t0m4snm.control_panel_backend.user.Workspace;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/** Logs de Vector: formatos de lote, campos de cada origen, filtros, paginación y permisos. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class LogTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private WorkspaceRepository workspaces;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private ServerRepository servers;

	@Autowired
	private ProjectRepository projects;

	@Autowired
	private UserDetailsService userDetailsService;

	@Autowired
	private LogService logs;

	@Autowired
	private JdbcTemplate jdbc;

	private AppUser admin;

	private AppUser member;

	private Server contabo;

	private Project shop;

	private String key;

	@BeforeEach
	void setUp() throws Exception {
		Workspace workspace = workspaces.save(new Workspace("Principal"));
		admin = users.save(new AppUser(workspace, "admin@example.com", "{noop}x", "Admin", UserRole.ADMIN));
		member = users.save(new AppUser(workspace, "member@example.com", "{noop}x", "Member", UserRole.MEMBER));
		contabo = new Server(workspace);
		contabo.setName("Contabo");
		contabo = servers.save(contabo);
		shop = projects.save(new Project(workspace, "Tienda"));
		users.flush();
		key = JsonPath.read(mvc.perform(post("/api/servers/{id}/ingest-keys", contabo.getId()).with(as(admin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Vector\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.key");
	}

	@Test
	void understandsDockerFileAndJournaldEvents() throws Exception {
		send("""
				[
				  {"message":"GET /api/stock 200 12ms","container_name":"/stock-app","stream":"stdout","timestamp":"2026-10-31T10:00:00.123456789Z"},
				  {"message":"[ERROR] Cannot connect to database","container_name":"/stock-app","stream":"stderr","timestamp":"2026-10-31T10:00:01Z"},
				  {"message":"Failed password for root from 1.2.3.4","file":"/host/var/log/auth.log","timestamp":"2026-10-31T10:00:02Z"},
				  {"message":"Out of memory: Killed process 123","_SYSTEMD_UNIT":"kernel","PRIORITY":"3"},
				  {"message":"   "}
				]
				""").andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(4));

		mvc.perform(get("/api/servers/{id}/logs", contabo.getId()).with(as(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(4))
				.andExpect(jsonPath("$[?(@.message == 'GET /api/stock 200 12ms')].source").value("stock-app"))
				.andExpect(jsonPath("$[?(@.message =~ /.*database/)].level").value("ERROR"))
				.andExpect(jsonPath("$[?(@.message =~ /Failed password.*/)].source").value("auth.log"))
				.andExpect(jsonPath("$[?(@.message =~ /Out of memory.*/)].level").value("ERROR"))
				.andExpect(jsonPath("$[?(@.message =~ /Out of memory.*/)].source").value("kernel"));

		mvc.perform(get("/api/servers/{id}/log-sources", contabo.getId()).with(as(admin)))
				.andExpect(jsonPath("$[?(@.name == 'stock-app')].lines").value(2))
				.andExpect(jsonPath("$[?(@.name == 'stock-app')].errors").value(1));
	}

	@Test
	void acceptsNewlineDelimitedJson() throws Exception {
		send("{\"message\":\"uno\",\"container_name\":\"web\"}\n{\"message\":\"dos\",\"container_name\":\"web\"}\n")
				.andExpect(jsonPath("$.accepted").value(2));
	}

	@Test
	void filtersBySourceLevelAndTextAndPages() throws Exception {
		send("""
				[
				  {"message":"INFO arranque","container_name":"api","timestamp":"2026-10-31T10:00:00Z"},
				  {"message":"WARN disco al 85%","container_name":"api","timestamp":"2026-10-31T10:00:01Z"},
				  {"message":"ERROR pedido 7 sin stock","container_name":"api","timestamp":"2026-10-31T10:00:02Z"},
				  {"message":"ERROR caddy","container_name":"proxy","timestamp":"2026-10-31T10:00:03Z"}
				]
				""");

		mvc.perform(get("/api/servers/{id}/logs", contabo.getId()).param("source", "api").param("level", "warn").with(as(admin)))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].message").value("ERROR pedido 7 sin stock"));
		mvc.perform(get("/api/servers/{id}/logs", contabo.getId()).param("q", "85%").with(as(admin)))
				.andExpect(jsonPath("$.length()").value(1));

		String page = mvc.perform(get("/api/servers/{id}/logs", contabo.getId()).param("limit", "2").with(as(admin)))
				.andExpect(jsonPath("$[0].message").value("ERROR caddy"))
				.andReturn().getResponse().getContentAsString();
		mvc.perform(get("/api/servers/{id}/logs", contabo.getId()).with(as(admin))
				.param("beforeTime", JsonPath.<String>read(page, "$[1].occurredAt"))
				.param("beforeId", JsonPath.read(page, "$[1].id").toString()))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].message").value("WARN disco al 85%"));
	}

	@Test
	void rejectsProjectKeysAndBadBatches() throws Exception {
		String projectKey = JsonPath.read(mvc.perform(post("/api/projects/{id}/ingest-keys", shop.getId()).with(as(admin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"App\"}"))
				.andReturn().getResponse().getContentAsString(), "$.key");
		mvc.perform(post("/api/ingest/logs").header("Authorization", "Bearer " + projectKey)
				.contentType(MediaType.APPLICATION_JSON).content("[{\"message\":\"x\"}]"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/ingest/logs").contentType(MediaType.APPLICATION_JSON).content("[{\"message\":\"x\"}]"))
				.andExpect(status().isUnauthorized());
		send("[]").andExpect(status().isBadRequest());
		send("esto no es json").andExpect(status().isBadRequest());
	}

	@Test
	void onlyAdminSeesLogsAndManagesServerKeys() throws Exception {
		mvc.perform(get("/api/servers/{id}/logs", contabo.getId()).with(as(member))).andExpect(status().isForbidden());
		mvc.perform(get("/api/servers/{id}/ingest-keys", contabo.getId()).with(as(member))).andExpect(status().isForbidden());
		mvc.perform(get("/api/servers/{id}/ingest-keys", contabo.getId()).with(as(admin)))
				.andExpect(jsonPath("$[0].name").value("Vector"));
	}

	@Test
	void purgeDropsLinesOlderThanAWeek() throws Exception {
		send("[{\"message\":\"reciente\"}]");
		jdbc.update("INSERT INTO log_line (server_id, source, message, occurred_at) VALUES (?, 'x', 'vieja', ?)",
				contabo.getId(), Timestamp.from(Instant.now().minus(8, ChronoUnit.DAYS)));

		assertThat(logs.purge()).isEqualTo(1);
		assertThat(jdbc.queryForList("SELECT message FROM log_line", String.class)).containsExactly("reciente");
	}

	private ResultActions send(String body) throws Exception {
		return mvc.perform(post("/api/ingest/logs").header("Authorization", "Bearer " + key)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private RequestPostProcessor as(AppUser appUser) {
		return request -> csrf().postProcessRequest(
				user(userDetailsService.loadUserByUsername(appUser.getEmail())).postProcessRequest(request));
	}

}
