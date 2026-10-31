package io.github.t0m4snm.control_panel_backend.timetracking;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

import com.jayway.jsonpath.JsonPath;

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

/**
 * Cronómetro, entradas manuales y tarifa. El {@code editor} es {@code EDITOR} del proyecto A y el
 * {@code viewer}, {@code VIEWER}; el proyecto B no es de ninguno de los dos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TimeTrackingTests {

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

	private AppUser admin;

	private AppUser editor;

	private AppUser viewer;

	private Project projectA;

	private Project projectB;

	@BeforeEach
	void setUp() {
		Workspace workspace = workspaces.save(new Workspace("Principal"));
		admin = users.save(new AppUser(workspace, "admin@example.com", "{noop}x", "Admin", UserRole.ADMIN));
		editor = users.save(new AppUser(workspace, "editor@example.com", "{noop}x", "Editor", UserRole.MEMBER));
		viewer = users.save(new AppUser(workspace, "viewer@example.com", "{noop}x", "Viewer", UserRole.MEMBER));
		projectA = projects.save(new Project(workspace, "A"));
		projectB = projects.save(new Project(workspace, "B"));
		members.save(new ProjectMember(projectA, editor, ProjectRole.EDITOR));
		members.save(new ProjectMember(projectA, viewer, ProjectRole.VIEWER));
	}

	@Test
	void noTimerRunningResponds204() throws Exception {
		mvc.perform(get("/api/timer").with(as(editor))).andExpect(status().isNoContent());
		mvc.perform(post("/api/timer/stop").with(as(editor))).andExpect(status().isNotFound());
	}

	@Test
	void startsAndStopsTimer() throws Exception {
		mvc.perform(json(post("/api/timer/start"), start(projectA, "Diseño")).with(as(editor)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.running").value(true))
				.andExpect(jsonPath("$.project.name").value("A"))
				.andExpect(jsonPath("$.description").value("Diseño"));

		mvc.perform(get("/api/timer").with(as(editor)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.running").value(true));

		mvc.perform(post("/api/timer/stop").with(as(editor)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.running").value(false))
				.andExpect(jsonPath("$.endedAt").exists());
		mvc.perform(get("/api/timer").with(as(editor))).andExpect(status().isNoContent());
	}

	@Test
	void startingAnotherTimerStopsThePreviousOne() throws Exception {
		mvc.perform(json(post("/api/timer/start"), start(projectA, "Primera")).with(as(admin)))
				.andExpect(status().isCreated());
		mvc.perform(json(post("/api/timer/start"), start(projectB, "Segunda")).with(as(admin)))
				.andExpect(status().isCreated());

		mvc.perform(get("/api/timer").with(as(admin))).andExpect(jsonPath("$.description").value("Segunda"));
		mvc.perform(get("/api/time-entries").with(as(admin)))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[?(@.description == 'Primera')].running").value(false));
	}

	@Test
	void onlyOwnersEditorsAndAdminsTrackTime() throws Exception {
		mvc.perform(json(post("/api/timer/start"), start(projectA, null)).with(as(viewer)))
				.andExpect(status().isForbidden());
		mvc.perform(json(post("/api/timer/start"), start(projectB, null)).with(as(editor)))
				.andExpect(status().isNotFound());
		mvc.perform(json(post("/api/timer/start"), start(projectB, null)).with(as(admin)))
				.andExpect(status().isCreated());
	}

	@Test
	void hourlyRateIsCopiedAndOnlyAdminSeesMoney() throws Exception {
		mvc.perform(json(put("/api/users/{id}/hourly-rate", editor.getId()), "{\"hourlyRate\":30}").with(as(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hourlyRate").value(30));

		String body = mvc.perform(json(post("/api/time-entries"), manual(projectA, hoursAgo(3), hoursAgo(1)))
				.with(as(editor)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.durationSeconds").value(7200))
				.andExpect(jsonPath("$.hourlyRate").doesNotExist())
				.andExpect(jsonPath("$.cost").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");

		// Cambiar la tarifa después no reescribe la entrada ya creada
		mvc.perform(json(put("/api/users/{id}/hourly-rate", editor.getId()), "{\"hourlyRate\":50}").with(as(admin)))
				.andExpect(status().isOk());
		mvc.perform(get("/api/time-entries").with(as(admin)))
				.andExpect(jsonPath("$[?(@.id == '" + id + "')].hourlyRate").value(30.0))
				.andExpect(jsonPath("$[?(@.id == '" + id + "')].cost").value(60.0));
	}

	@Test
	void membersSeeAndEditOnlyTheirOwnEntries() throws Exception {
		mvc.perform(json(post("/api/time-entries"), manual(projectA, hoursAgo(5), hoursAgo(4))).with(as(editor)))
				.andExpect(status().isCreated());
		String adminEntry = mvc.perform(json(post("/api/time-entries"), manual(projectA, hoursAgo(3), hoursAgo(2)))
				.with(as(admin)))
				.andReturn().getResponse().getContentAsString();

		mvc.perform(get("/api/time-entries").with(as(editor))).andExpect(jsonPath("$.length()").value(1));
		mvc.perform(get("/api/time-entries").param("userId", admin.getId().toString()).with(as(editor)))
				.andExpect(status().isForbidden());
		mvc.perform(json(put("/api/time-entries/{id}", JsonPath.<String>read(adminEntry, "$.id")),
				manual(projectA, hoursAgo(3), hoursAgo(1))).with(as(editor)))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/time-entries").with(as(admin))).andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void validatesManualEntries() throws Exception {
		mvc.perform(json(post("/api/time-entries"), manual(projectA, hoursAgo(2), null)).with(as(editor)))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/time-entries"), manual(projectA, hoursAgo(1), hoursAgo(2))).with(as(editor)))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/time-entries"), manual(projectA, hoursAgo(1), hoursAgo(-2))).with(as(editor)))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/time-entries"), """
				{"projectId":"%s","startedAt":"%s","endedAt":"%s","hourlyRate":100}
				""".formatted(projectA.getId(), hoursAgo(2), hoursAgo(1))).with(as(editor)))
				.andExpect(status().isForbidden());
	}

	@Test
	void usersEndpointIsAdminOnly() throws Exception {
		mvc.perform(get("/api/users").with(as(editor))).andExpect(status().isForbidden());
		mvc.perform(json(put("/api/users/{id}/hourly-rate", editor.getId()), "{\"hourlyRate\":10}").with(as(editor)))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/users").with(as(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3));
	}

	private static String start(Project project, String description) {
		return description == null ? "{\"projectId\":\"%s\"}".formatted(project.getId())
				: "{\"projectId\":\"%s\",\"description\":\"%s\"}".formatted(project.getId(), description);
	}

	private static String manual(Project project, Instant startedAt, Instant endedAt) {
		return endedAt == null ? "{\"projectId\":\"%s\",\"startedAt\":\"%s\"}".formatted(project.getId(), startedAt)
				: "{\"projectId\":\"%s\",\"startedAt\":\"%s\",\"endedAt\":\"%s\"}".formatted(project.getId(),
						startedAt, endedAt);
	}

	private static Instant hoursAgo(int hours) {
		return Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(hours, ChronoUnit.HOURS);
	}

	private RequestPostProcessor as(AppUser appUser) {
		return request -> csrf().postProcessRequest(
				user(userDetailsService.loadUserByUsername(appUser.getEmail())).postProcessRequest(request));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
