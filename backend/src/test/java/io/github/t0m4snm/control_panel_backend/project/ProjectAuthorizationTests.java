package io.github.t0m4snm.control_panel_backend.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.github.t0m4snm.control_panel_backend.TestcontainersConfiguration;
import io.github.t0m4snm.control_panel_backend.user.AppUser;
import io.github.t0m4snm.control_panel_backend.user.AppUserRepository;
import io.github.t0m4snm.control_panel_backend.user.UserRole;
import io.github.t0m4snm.control_panel_backend.user.Workspace;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/**
 * Regla de visibilidad por proyecto: el admin ve todo su workspace; un miembro, solo sus proyectos,
 * y lo que puede hacer en cada uno depende de su rol.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ProjectAuthorizationTests {

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

	private AppUser owner;

	private AppUser editor;

	private AppUser viewer;

	private AppUser outsider;

	private AppUser otherWorkspaceUser;

	private Project shared;

	private Project adminOnly;

	private Project otherWorkspaceProject;

	@BeforeEach
	void setUp() {
		Workspace workspace = workspaces.save(new Workspace("Principal"));
		Workspace otherWorkspace = workspaces.save(new Workspace("Otro"));
		admin = newUser(workspace, "admin@example.com", UserRole.ADMIN);
		owner = newUser(workspace, "owner@example.com", UserRole.MEMBER);
		editor = newUser(workspace, "editor@example.com", UserRole.MEMBER);
		viewer = newUser(workspace, "viewer@example.com", UserRole.MEMBER);
		outsider = newUser(workspace, "outsider@example.com", UserRole.MEMBER);
		otherWorkspaceUser = newUser(otherWorkspace, "otro@example.com", UserRole.ADMIN);

		shared = projects.save(new Project(workspace, "Compartido"));
		adminOnly = projects.save(new Project(workspace, "Solo admin"));
		otherWorkspaceProject = projects.save(new Project(otherWorkspace, "De otro workspace"));
		members.save(new ProjectMember(shared, owner, ProjectRole.OWNER));
		members.save(new ProjectMember(shared, editor, ProjectRole.EDITOR));
		members.save(new ProjectMember(shared, viewer, ProjectRole.VIEWER));
	}

	@Test
	void adminSeesEveryProjectOfHisWorkspaceOnly() throws Exception {
		mvc.perform(get("/api/projects").with(as(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].name").value("Compartido"))
				.andExpect(jsonPath("$[0].myRole").doesNotExist())
				.andExpect(jsonPath("$[0].permissions.delete").value(true))
				.andExpect(jsonPath("$[1].name").value("Solo admin"));
	}

	@Test
	void memberSeesOnlyHisProjects() throws Exception {
		mvc.perform(get("/api/projects").with(as(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("Compartido"))
				.andExpect(jsonPath("$[0].myRole").value("VIEWER"))
				.andExpect(jsonPath("$[0].permissions.edit").value(false));

		mvc.perform(get("/api/projects").with(as(outsider)))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void hiddenProjectsRespond404() throws Exception {
		mvc.perform(get("/api/projects/{id}", adminOnly.getId()).with(as(owner))).andExpect(status().isNotFound());
		mvc.perform(get("/api/projects/{id}/members", adminOnly.getId()).with(as(owner)))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/projects/{id}", otherWorkspaceProject.getId()).with(as(admin)))
				.andExpect(status().isNotFound());
		mvc.perform(put("/api/projects/{id}", otherWorkspaceProject.getId()).with(as(admin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Robado\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void onlyAdminCreatesProjectsAndBecomesOwner() throws Exception {
		mvc.perform(json(post("/api/projects"), "{\"name\":\"Nuevo\"}").with(as(owner)))
				.andExpect(status().isForbidden());

		mvc.perform(json(post("/api/projects"),
				"{\"name\":\" Nuevo \",\"repoUrl\":\"https://github.com/T0M4SNM/nuevo\"}").with(as(admin)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Nuevo"))
				.andExpect(jsonPath("$.status").value("IDEA"))
				.andExpect(jsonPath("$.myRole").value("OWNER"));
	}

	@Test
	void validatesProjectData() throws Exception {
		mvc.perform(json(post("/api/projects"), "{\"name\":\" \"}").with(as(admin)))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/projects"), "{\"name\":\"X\",\"publicUrl\":\"no es una url\"}").with(as(admin)))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/projects"), "{\"name\":\"X\",\"status\":\"ARCHIVED\"}").with(as(admin)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void ownerAndEditorCanEditButViewerCannot() throws Exception {
		mvc.perform(json(put("/api/projects/{id}", shared.getId()), "{\"name\":\"Por viewer\"}").with(as(viewer)))
				.andExpect(status().isForbidden());

		mvc.perform(json(put("/api/projects/{id}", shared.getId()),
				"{\"name\":\"Renombrado\",\"status\":\"PRODUCTION\"}").with(as(editor)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Renombrado"))
				.andExpect(jsonPath("$.status").value("PRODUCTION"));
	}

	@Test
	void onlyAdminDeletesProjects() throws Exception {
		mvc.perform(delete("/api/projects/{id}", shared.getId()).with(as(owner))).andExpect(status().isForbidden());

		mvc.perform(delete("/api/projects/{id}", shared.getId()).with(as(admin))).andExpect(status().isNoContent());
		assertThat(projects.findById(shared.getId())).isEmpty();
		assertThat(members.findByProjectIdWithUser(shared.getId())).isEmpty();
	}

	@Test
	void adminCanDeleteProjectHeCreated() throws Exception {
		String body = mvc.perform(json(post("/api/projects"), "{\"name\":\"Temporal\"}").with(as(admin)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");

		mvc.perform(delete("/api/projects/{id}", id).with(as(admin))).andExpect(status().isNoContent());
		mvc.perform(get("/api/projects/{id}", id).with(as(admin))).andExpect(status().isNotFound());
	}

	@Test
	void anyMemberCanListMembers() throws Exception {
		mvc.perform(get("/api/projects/{id}/members", shared.getId()).with(as(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3));
	}

	@Test
	void ownerManagesMembersButEditorCannot() throws Exception {
		mvc.perform(json(put("/api/projects/{id}/members/{userId}", shared.getId(), outsider.getId()),
				"{\"role\":\"VIEWER\"}").with(as(editor)))
				.andExpect(status().isForbidden());

		mvc.perform(json(put("/api/projects/{id}/members/{userId}", shared.getId(), outsider.getId()),
				"{\"role\":\"VIEWER\"}").with(as(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("VIEWER"));
		mvc.perform(get("/api/projects").with(as(outsider))).andExpect(jsonPath("$.length()").value(1));

		mvc.perform(json(put("/api/projects/{id}/members/{userId}", shared.getId(), outsider.getId()),
				"{\"role\":\"EDITOR\"}").with(as(owner)))
				.andExpect(jsonPath("$.role").value("EDITOR"));

		mvc.perform(delete("/api/projects/{id}/members/{userId}", shared.getId(), outsider.getId()).with(as(owner)))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/projects").with(as(outsider))).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void cannotAddUserFromAnotherWorkspace() throws Exception {
		mvc.perform(json(put("/api/projects/{id}/members/{userId}", shared.getId(), otherWorkspaceUser.getId()),
				"{\"role\":\"VIEWER\"}").with(as(admin)))
				.andExpect(status().isNotFound());
	}

	@Test
	void requiresSession() throws Exception {
		mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
	}

	private AppUser newUser(Workspace workspace, String email, UserRole role) {
		return users.save(new AppUser(workspace, email, "{noop}x", email, role));
	}

	private RequestPostProcessor as(AppUser appUser) {
		return request -> csrf().postProcessRequest(
				user(userDetailsService.loadUserByUsername(appUser.getEmail())).postProcessRequest(request));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
