package io.github.t0m4snm.control_panel_backend.monitoring;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.TestcontainersConfiguration;
import io.github.t0m4snm.control_panel_backend.domain.Domain;
import io.github.t0m4snm.control_panel_backend.domain.DomainRepository;
import io.github.t0m4snm.control_panel_backend.project.Project;
import io.github.t0m4snm.control_panel_backend.project.ProjectMember;
import io.github.t0m4snm.control_panel_backend.project.ProjectMemberRepository;
import io.github.t0m4snm.control_panel_backend.project.ProjectRepository;
import io.github.t0m4snm.control_panel_backend.project.ProjectRole;
import io.github.t0m4snm.control_panel_backend.server.ProjectServer;
import io.github.t0m4snm.control_panel_backend.server.ProjectServerRepository;
import io.github.t0m4snm.control_panel_backend.server.Server;
import io.github.t0m4snm.control_panel_backend.server.ServerRepository;
import io.github.t0m4snm.control_panel_backend.user.AppUser;
import io.github.t0m4snm.control_panel_backend.user.AppUserRepository;
import io.github.t0m4snm.control_panel_backend.user.UserRole;
import io.github.t0m4snm.control_panel_backend.user.Workspace;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/**
 * El miembro es {@code VIEWER} del proyecto A, que usa el servidor S y el dominio app.ejemplo.com.
 * Ve las comprobaciones de A, de S y del dominio; no ve la del proyecto B ni la que no está ligada a
 * nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HealthCheckApiTests {

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
	private ServerRepository servers;

	@Autowired
	private ProjectServerRepository links;

	@Autowired
	private DomainRepository domains;

	@Autowired
	private HealthCheckRepository checks;

	@Autowired
	private CheckResultRepository results;

	@Autowired
	private UserDetailsService userDetailsService;

	private Workspace workspace;

	private AppUser admin;

	private AppUser member;

	private Project projectA;

	private Project projectB;

	private HealthCheck webA;

	private HealthCheck pingB;

	@BeforeEach
	void setUp() {
		workspace = workspaces.save(new Workspace("Principal"));
		admin = users.save(new AppUser(workspace, "admin@example.com", "{noop}x", "Admin", UserRole.ADMIN));
		member = users.save(new AppUser(workspace, "member@example.com", "{noop}x", "Member", UserRole.MEMBER));
		projectA = projects.save(new Project(workspace, "A"));
		projectB = projects.save(new Project(workspace, "B"));
		members.save(new ProjectMember(projectA, member, ProjectRole.VIEWER));

		Server server = new Server(workspace);
		server.setName("S");
		servers.save(server);
		links.save(new ProjectServer(projectA, server, BigDecimal.TEN));
		Domain domain = new Domain(workspace);
		domain.setName("app.ejemplo.com");
		domain.setProject(projectA);
		domains.save(domain);

		webA = newCheck("1 Web A", CheckType.HTTP, "https://a.ejemplo.com");
		webA.setProject(projectA);
		HealthCheck port = newCheck("2 Puerto S", CheckType.TCP_PORT, "10.0.0.1");
		port.setPort(22);
		port.setServer(server);
		HealthCheck ssl = newCheck("3 SSL dominio", CheckType.SSL, "app.ejemplo.com");
		ssl.setDomain(domain);
		pingB = newCheck("4 Ping B", CheckType.PING, "10.0.0.2");
		pingB.setProject(projectB);
		newCheck("5 Suelta", CheckType.PING, "10.0.0.3");
	}

	@Test
	void memberSeesChecksOfHisProjectsServersAndDomains() throws Exception {
		mvc.perform(get("/api/health-checks").with(as(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].name").value("1 Web A"))
				.andExpect(jsonPath("$[1].name").value("2 Puerto S"))
				.andExpect(jsonPath("$[2].name").value("3 SSL dominio"));
		mvc.perform(get("/api/health-checks/{id}", pingB.getId()).with(as(member))).andExpect(status().isNotFound());
		mvc.perform(get("/api/health-checks").with(as(admin))).andExpect(jsonPath("$.length()").value(5));
	}

	@Test
	void projectEndpointIncludesChecksOfItsServersAndDomains() throws Exception {
		mvc.perform(get("/api/projects/{id}/health-checks", projectA.getId()).with(as(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3));
		mvc.perform(get("/api/projects/{id}/health-checks", projectB.getId()).with(as(member)))
				.andExpect(status().isNotFound());
	}

	@Test
	void onlyAdminManagesChecks() throws Exception {
		mvc.perform(json(post("/api/health-checks"), check("HTTP", "https://x.com", null)).with(as(member)))
				.andExpect(status().isForbidden());
		mvc.perform(delete("/api/health-checks/{id}", webA.getId()).with(as(member)))
				.andExpect(status().isForbidden());
		mvc.perform(json(post("/api/health-checks"), check("HTTP", "https://x.com", null)).with(as(admin)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.intervalSeconds").value(300))
				.andExpect(jsonPath("$.failureThreshold").value(3))
				.andExpect(jsonPath("$.currentStatus").value("UNKNOWN"));
	}

	@Test
	void validatesTargetByType() throws Exception {
		mvc.perform(json(post("/api/health-checks"), check("HTTP", "ftp://x.com", null)).with(as(admin)))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/health-checks"), check("HTTP", "https://x.com", 8443)).with(as(admin)))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/health-checks"), check("TCP_PORT", "x.com", null)).with(as(admin)))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/health-checks"), check("PING", "https://x.com", null)).with(as(admin)))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/health-checks"),
				"{\"name\":\"X\",\"type\":\"PING\",\"target\":\"x.com\",\"intervalSeconds\":30}").with(as(admin)))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/health-checks"), check("TCP_PORT", "2001:db8::1", 5432)).with(as(admin)))
				.andExpect(status().isCreated());
	}

	@Test
	void changingTheTargetResetsTheStatus() throws Exception {
		webA.record(true, Instant.now());
		checks.flush();

		mvc.perform(json(put("/api/health-checks/{id}", webA.getId()), check("HTTP", "https://a.ejemplo.com", null))
				.with(as(admin)))
				.andExpect(jsonPath("$.currentStatus").value("UP"));
		mvc.perform(json(put("/api/health-checks/{id}", webA.getId()), check("HTTP", "https://b.ejemplo.com", null))
				.with(as(admin)))
				.andExpect(jsonPath("$.currentStatus").value("UNKNOWN"))
				.andExpect(jsonPath("$.lastCheckedAt").doesNotExist());
	}

	@Test
	void resultsAreNewestFirstAndLimited() throws Exception {
		Instant t0 = Instant.parse("2026-10-31T10:00:00Z");
		for (int i = 0; i < 5; i++) {
			results.save(new CheckResult(webA, t0.plusSeconds(300L * i), i % 2 == 0, 120, 200, null, null));
		}
		mvc.perform(get("/api/health-checks/{id}/results", webA.getId()).param("limit", "2").with(as(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].checkedAt").value("2026-10-31T10:20:00Z"))
				.andExpect(jsonPath("$[1].checkedAt").value("2026-10-31T10:15:00Z"));
		mvc.perform(get("/api/health-checks/{id}/results", webA.getId()).param("limit", "501").with(as(admin)))
				.andExpect(status().isBadRequest());
	}

	private HealthCheck newCheck(String name, CheckType type, String target) {
		HealthCheck check = new HealthCheck(workspace);
		check.setName(name);
		check.setType(type);
		check.setTarget(target);
		return checks.save(check);
	}

	private static String check(String type, String target, Integer port) {
		return "{\"name\":\"Comprobación\",\"type\":\"%s\",\"target\":\"%s\"%s}".formatted(type, target,
				port == null ? "" : ",\"port\":" + port);
	}

	private RequestPostProcessor as(AppUser appUser) {
		return request -> csrf().postProcessRequest(
				user(userDetailsService.loadUserByUsername(appUser.getEmail())).postProcessRequest(request));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
