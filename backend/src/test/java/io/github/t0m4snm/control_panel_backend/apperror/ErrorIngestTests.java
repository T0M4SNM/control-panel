package io.github.t0m4snm.control_panel_backend.apperror;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.github.t0m4snm.control_panel_backend.TestcontainersConfiguration;
import io.github.t0m4snm.control_panel_backend.notification.Notification;
import io.github.t0m4snm.control_panel_backend.notification.NotificationRepository;
import io.github.t0m4snm.control_panel_backend.notification.NotificationType;
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

/**
 * Envío de errores con clave, agrupación, avisos y permisos. El {@code viewer} es lector del proyecto
 * Stock; el {@code outsider}, de ninguno.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ErrorIngestTests {

	private static final String STACK = "TypeError: x\\n    at getStock (/app/src/stock.js:%d:17)\\n    at next (/app/node_modules/express/router.js:9:3)";

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
	private NotificationRepository notifications;

	@Autowired
	private UserDetailsService userDetailsService;

	private AppUser admin;

	private AppUser viewer;

	private AppUser outsider;

	private Project stock;

	private String key;

	@BeforeEach
	void setUp() throws Exception {
		Workspace workspace = workspaces.save(new Workspace("Principal"));
		admin = users.save(new AppUser(workspace, "admin@example.com", "{noop}x", "Admin", UserRole.ADMIN));
		viewer = users.save(new AppUser(workspace, "viewer@example.com", "{noop}x", "Viewer", UserRole.MEMBER));
		outsider = users.save(new AppUser(workspace, "outsider@example.com", "{noop}x", "Outsider", UserRole.MEMBER));
		stock = projects.save(new Project(workspace, "Stock"));
		members.save(new ProjectMember(stock, viewer, ProjectRole.VIEWER));
		users.flush();

		String body = mvc.perform(post("/api/projects/{id}/ingest-keys", stock.getId()).with(as(admin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Producción\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.key").value(org.hamcrest.Matchers.startsWith("cpk_")))
				.andReturn().getResponse().getContentAsString();
		key = JsonPath.read(body, "$.key");
	}

	@Test
	void sameErrorIsGroupedAndAlertsOnlyOnce() throws Exception {
		send(error("TypeError", "Cannot read 'id'", 42)).andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(1));
		send(error("TypeError", "Cannot read 'id'", 57)).andExpect(status().isAccepted());
		send(error("RangeError", "Invalid array length", 10)).andExpect(status().isAccepted());

		mvc.perform(get("/api/projects/{id}/errors", stock.getId()).with(as(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[?(@.type == 'TypeError')].occurrences").value(2));

		assertThat(notifications.findByUserId(admin.getId())).hasSize(2)
				.allSatisfy(n -> assertThat(n.getType()).isEqualTo(NotificationType.APP_ERROR));
		assertThat(notifications.findByUserId(viewer.getId())).hasSize(2);
		assertThat(notifications.findByUserId(outsider.getId())).isEmpty();
	}

	@Test
	void resolvedErrorThatComesBackIsReopenedAndAlertsAgain() throws Exception {
		send(error("TypeError", "Cannot read 'id'", 42));
		String groupId = JsonPath.read(mvc.perform(get("/api/projects/{id}/errors", stock.getId()).with(as(admin)))
				.andReturn().getResponse().getContentAsString(), "$[0].id");

		mvc.perform(post("/api/errors/{id}/resolve", groupId).with(as(viewer))).andExpect(status().isForbidden());
		mvc.perform(post("/api/errors/{id}/resolve", groupId).with(as(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RESOLVED"));
		mvc.perform(get("/api/projects/{id}/errors", stock.getId()).with(as(admin))).andExpect(jsonPath("$.length()").value(0));

		send(error("TypeError", "Cannot read 'id'", 44));

		mvc.perform(get("/api/errors/{id}", groupId).with(as(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.group.status").value("OPEN"))
				.andExpect(jsonPath("$.group.occurrences").value(2))
				.andExpect(jsonPath("$.events.length()").value(2))
				.andExpect(jsonPath("$.events[0].context").value("{\"url\":\"/api/stock/7\",\"method\":\"GET\"}"));
		assertThat(notifications.findByUserId(admin.getId())).extracting(Notification::getTitle)
				.containsExactlyInAnyOrder("Nuevo error en Stock", "Vuelve un error en Stock");
	}

	@Test
	void rejectsMissingWrongAndRevokedKeys() throws Exception {
		mvc.perform(post("/api/ingest/errors").contentType(MediaType.APPLICATION_JSON).content(error("E", "m", 1)))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/ingest/errors").header("Authorization", "Bearer cpk_falsa")
				.contentType(MediaType.APPLICATION_JSON).content(error("E", "m", 1)))
				.andExpect(status().isUnauthorized());

		String keyId = JsonPath.read(mvc.perform(get("/api/projects/{id}/ingest-keys", stock.getId()).with(as(admin)))
				.andExpect(jsonPath("$[0].keyPrefix").value(key.substring(0, 10)))
				.andReturn().getResponse().getContentAsString(), "$[0].id");
		mvc.perform(delete("/api/ingest-keys/{id}", keyId).with(as(admin))).andExpect(status().isNoContent());
		send(error("E", "m", 1)).andExpect(status().isUnauthorized());
	}

	@Test
	void validatesBatchSize() throws Exception {
		send("{\"errors\":[]}").andExpect(status().isBadRequest());
		String many = "{\"errors\":[" + String.join(",", java.util.Collections.nCopies(51, "{\"type\":\"E\",\"message\":\"m\"}")) + "]}";
		send(many).andExpect(status().isBadRequest());
	}

	@Test
	void onlyAdminManagesKeysAndOnlyProjectMembersSeeErrors() throws Exception {
		mvc.perform(post("/api/projects/{id}/ingest-keys", stock.getId()).with(as(viewer))
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/projects/{id}/ingest-keys", stock.getId()).with(as(viewer))).andExpect(status().isForbidden());
		mvc.perform(get("/api/projects/{id}/errors", stock.getId()).with(as(outsider))).andExpect(status().isNotFound());
	}

	private ResultActions send(String json) throws Exception {
		return mvc.perform(post("/api/ingest/errors").header("Authorization", "Bearer " + key)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String error(String type, String message, int line) {
		return """
				{"errors":[{"type":"%s","message":"%s","stack":"%s","environment":"production","release":"1.4.0",
				            "context":{"url":"/api/stock/7","method":"GET"}}]}
				""".formatted(type, message, STACK.formatted(line));
	}

	private RequestPostProcessor as(AppUser appUser) {
		return request -> csrf().postProcessRequest(
				user(userDetailsService.loadUserByUsername(appUser.getEmail())).postProcessRequest(request));
	}

}
