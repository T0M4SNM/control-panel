package io.github.t0m4snm.control_panel_backend.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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

import io.github.t0m4snm.control_panel_backend.TestcontainersConfiguration;
import io.github.t0m4snm.control_panel_backend.project.Project;
import io.github.t0m4snm.control_panel_backend.project.ProjectMember;
import io.github.t0m4snm.control_panel_backend.project.ProjectMemberRepository;
import io.github.t0m4snm.control_panel_backend.project.ProjectRepository;
import io.github.t0m4snm.control_panel_backend.project.ProjectRole;
import io.github.t0m4snm.control_panel_backend.user.AppUser;
import io.github.t0m4snm.control_panel_backend.user.AppUserRepository;
import io.github.t0m4snm.control_panel_backend.user.UserRole;
import io.github.t0m4snm.control_panel_backend.user.Workspace;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/** Visitas desde el script de la web, visitantes anónimos, gráfica y permisos. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class VisitTests {

	private static final String FIREFOX = "Mozilla/5.0 (Windows NT 10.0; rv:140.0) Gecko/20100101 Firefox/140.0";

	private static final String CHROME = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/141.0 Mobile Safari/537.36";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private WorkspaceRepository workspaces;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private ProjectRepository projects;

	@Autowired
	private ProjectMemberRepository members;

	@Autowired
	private UserDetailsService userDetailsService;

	@Autowired
	private VisitService visits;

	@Autowired
	private JdbcTemplate jdbc;

	private AppUser viewer;

	private AppUser outsider;

	private Project shop;

	@BeforeEach
	void setUp() {
		Workspace workspace = workspaces.save(new Workspace("Principal"));
		viewer = users.save(new AppUser(workspace, "viewer@example.com", "{noop}x", "Viewer", UserRole.MEMBER));
		outsider = users.save(new AppUser(workspace, "outsider@example.com", "{noop}x", "Outsider", UserRole.MEMBER));
		shop = new Project(workspace, "Tienda");
		shop.setPublicUrl("https://www.tienda.example");
		shop = projects.save(shop);
		members.save(new ProjectMember(shop, viewer, ProjectRole.VIEWER));
		users.flush();
	}

	@Test
	void countsVisitorsWithoutCookiesAndDropsQueryStrings() throws Exception {
		visit("VIEW", "/productos?token=secreto", "10.0.0.1", FIREFOX).andExpect(status().isAccepted());
		visit("VIEW", "/carrito", "10.0.0.1", FIREFOX).andExpect(status().isAccepted());
		visit("PING", null, "10.0.0.1", FIREFOX).andExpect(status().isAccepted());
		visit("VIEW", "/productos", "10.0.0.2", CHROME).andExpect(status().isAccepted());

		mvc.perform(get("/api/projects/{id}/visits", shop.getId()).param("range", "day").with(as(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.visitors").value(2))
				.andExpect(jsonPath("$.views").value(3))
				.andExpect(jsonPath("$.online").value(2))
				.andExpect(jsonPath("$.points.length()").value(24))
				.andExpect(jsonPath("$.points[23].visitors").value(2))
				.andExpect(jsonPath("$.topPages[0].name").value("/productos"))
				.andExpect(jsonPath("$.topPages[0].count").value(2))
				.andExpect(jsonPath("$.topReferrers[0].name").value("duckduckgo.com"));

		assertThat(jdbc.queryForList("SELECT path FROM visit_event WHERE path IS NOT NULL", String.class))
				.noneMatch(p -> p.contains("?"));
		// Ni IP ni navegador: solo el hash
		assertThat(jdbc.queryForList("SELECT visitor FROM visit_event", String.class))
				.allMatch(v -> v.matches("[0-9a-f]{64}"));
	}

	@Test
	void dailyRangesFillEmptyDays() throws Exception {
		visit("VIEW", "/", "10.0.0.1", FIREFOX);

		mvc.perform(get("/api/projects/{id}/visits", shop.getId()).param("range", "MONTH").with(as(viewer)))
				.andExpect(jsonPath("$.points.length()").value(30))
				.andExpect(jsonPath("$.points[0].visitors").value(0))
				.andExpect(jsonPath("$.points[29].visitors").value(1))
				.andExpect(jsonPath("$.stepSeconds").value(86400));
	}

	@Test
	void ignoresBotsAndRejectsOtherSitesAndUnknownProjects() throws Exception {
		visit("VIEW", "/", "10.0.0.1", "Mozilla/5.0 (compatible; Googlebot/2.1)").andExpect(status().isAccepted());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_event", Long.class)).isZero();

		mvc.perform(post("/api/ingest/visits").contentType(MediaType.TEXT_PLAIN).header("User-Agent", FIREFOX)
				.header("Origin", "https://otra-web.example")
				.content("{\"project\":\"%s\",\"kind\":\"VIEW\",\"path\":\"/\"}".formatted(shop.getId())))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/ingest/visits").contentType(MediaType.TEXT_PLAIN).header("User-Agent", FIREFOX)
				.content("{\"project\":\"%s\",\"kind\":\"VIEW\"}".formatted(UUID.randomUUID())))
				.andExpect(status().isNotFound());
		mvc.perform(post("/api/ingest/visits").contentType(MediaType.TEXT_PLAIN).header("User-Agent", FIREFOX)
				.content("no es json"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void onlyProjectMembersSeeVisits() throws Exception {
		mvc.perform(get("/api/projects/{id}/visits", shop.getId()).with(as(outsider))).andExpect(status().isNotFound());
		mvc.perform(get("/api/projects/{id}/visits", shop.getId()).param("range", "SIGLO").with(as(viewer)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void purgeDropsOldVisitsAndPastSalts() throws Exception {
		visit("VIEW", "/", "10.0.0.1", FIREFOX);
		jdbc.update("INSERT INTO visit_salt (day, salt) VALUES (?, ?)", LocalDate.now().minusDays(1), new byte[32]);
		jdbc.update("INSERT INTO visit_event (project_id, visitor, kind, occurred_at) VALUES (?, 'x', 'VIEW', ?)",
				shop.getId(), Timestamp.from(Instant.now().minus(91, ChronoUnit.DAYS)));

		assertThat(visits.purge()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_event", Long.class)).isEqualTo(1);
		// La sal de hoy puede estar solo en memoria si otro test la creó antes; lo que importa es que no quede la de ayer
		assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_salt WHERE day < ?", Long.class, LocalDate.now())).isZero();
	}

	@Test
	void sameSiteAcceptsSubdomainsWithOrWithoutWww() {
		assertThat(VisitService.sameSite("tienda.example", "www.tienda.example")).isTrue();
		assertThat(VisitService.sameSite("www.tienda.example", "tienda.example")).isTrue();
		assertThat(VisitService.sameSite("app.tienda.example", "tienda.example")).isTrue();
		assertThat(VisitService.sameSite("mitienda.example", "tienda.example")).isFalse();
		assertThat(VisitService.sameSite(null, "tienda.example")).isFalse();
	}

	private ResultActions visit(String kind, String path, String address, String userAgent) throws Exception {
		String body = path == null ? "{\"project\":\"%s\",\"kind\":\"%s\"}".formatted(shop.getId(), kind)
				: "{\"project\":\"%s\",\"kind\":\"%s\",\"path\":\"%s\",\"referrer\":\"duckduckgo.com\"}"
						.formatted(shop.getId(), kind, path);
		return mvc.perform(post("/api/ingest/visits").contentType(MediaType.TEXT_PLAIN)
				.header("User-Agent", userAgent)
				.header("Origin", "https://tienda.example")
				.with(request -> {
					request.setRemoteAddr(address);
					return request;
				})
				.content(body));
	}

	private RequestPostProcessor as(AppUser appUser) {
		return user(userDetailsService.loadUserByUsername(appUser.getEmail()));
	}

}
