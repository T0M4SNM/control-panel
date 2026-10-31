package io.github.t0m4snm.control_panel_backend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

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
import io.github.t0m4snm.control_panel_backend.contract.BillingPeriod;
import io.github.t0m4snm.control_panel_backend.contract.Contract;
import io.github.t0m4snm.control_panel_backend.contract.ContractRepository;
import io.github.t0m4snm.control_panel_backend.contract.ContractType;
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
 * Escenario: {@code ejemplo.com} con contrato y dos subdominios, {@code app.ejemplo.com} del
 * proyecto A y {@code api.ejemplo.com} del proyecto B. El miembro es {@code VIEWER} de A.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DomainTests {

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
	private ContractRepository contracts;

	@Autowired
	private DomainRepository domains;

	@Autowired
	private UserDetailsService userDetailsService;

	private Workspace workspace;

	private AppUser admin;

	private AppUser member;

	private Project projectA;

	private Domain root;

	private Domain app;

	private Domain api;

	@BeforeEach
	void setUp() {
		workspace = workspaces.save(new Workspace("Principal"));
		admin = users.save(new AppUser(workspace, "admin@example.com", "{noop}x", "Admin", UserRole.ADMIN));
		member = users.save(new AppUser(workspace, "member@example.com", "{noop}x", "Member", UserRole.MEMBER));
		projectA = projects.save(new Project(workspace, "A"));
		Project projectB = projects.save(new Project(workspace, "B"));
		members.save(new ProjectMember(projectA, member, ProjectRole.VIEWER));

		Contract contract = new Contract(workspace);
		contract.setName("Dominio ejemplo.com");
		contract.setProvider("IONOS");
		contract.setType(ContractType.DOMAIN);
		contract.setAmount(new BigDecimal("12.00"));
		contract.setBillingPeriod(BillingPeriod.YEARLY);
		contract.setRenewalDate(LocalDate.of(2027, 5, 1));
		contracts.save(contract);

		root = newDomain("ejemplo.com", null, null);
		root.setContract(contract);
		app = newDomain("app.ejemplo.com", root, projectA);
		api = newDomain("api.ejemplo.com", root, projectB);
	}

	@Test
	void memberSeesOnlyDomainsOfHisProjectsWithoutContract() throws Exception {
		mvc.perform(get("/api/domains").with(as(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("app.ejemplo.com"))
				.andExpect(jsonPath("$[0].project.name").value("A"))
				.andExpect(jsonPath("$[0].contract").doesNotExist());

		mvc.perform(get("/api/domains/{id}", api.getId()).with(as(member))).andExpect(status().isNotFound());
		mvc.perform(get("/api/domains/{id}", root.getId()).with(as(member))).andExpect(status().isNotFound());
		mvc.perform(get("/api/projects/{id}/domains", projectA.getId()).with(as(member)))
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void subdomainInheritsContractOfRoot() throws Exception {
		mvc.perform(get("/api/domains/{id}", app.getId()).with(as(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.parent.name").value("ejemplo.com"))
				.andExpect(jsonPath("$.contract.name").value("Dominio ejemplo.com"))
				.andExpect(jsonPath("$.contract.renewalDate").value("2027-05-01"))
				.andExpect(jsonPath("$.contract.inherited").value(true));
		mvc.perform(get("/api/domains/{id}", root.getId()).with(as(admin)))
				.andExpect(jsonPath("$.contract.inherited").value(false));
	}

	@Test
	void subdomainCannotHaveItsOwnContract() throws Exception {
		Contract other = contracts.findByWorkspaceIdOrderByRenewalDate(workspace.getId()).getFirst();
		mvc.perform(json(post("/api/domains"), """
				{"name":"blog.ejemplo.com","parentId":"%s","contractId":"%s"}
				""".formatted(root.getId(), other.getId())).with(as(admin)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void subdomainMustEndWithParentName() throws Exception {
		mvc.perform(json(post("/api/domains"), """
				{"name":"blog.otro.com","parentId":"%s"}
				""".formatted(root.getId())).with(as(admin)))
				.andExpect(status().isBadRequest());

		mvc.perform(json(post("/api/domains"), """
				{"name":"Blog.Ejemplo.com","parentId":"%s","projectId":"%s"}
				""".formatted(root.getId(), projectA.getId())).with(as(admin)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("blog.ejemplo.com"))
				.andExpect(jsonPath("$.contract.inherited").value(true));
	}

	@Test
	void validatesDomainName() throws Exception {
		mvc.perform(json(post("/api/domains"), "{\"name\":\"sin-punto\"}").with(as(admin)))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/domains"), "{\"name\":\"con espacios.com\"}").with(as(admin)))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/domains"), "{\"name\":\"-guion.com\"}").with(as(admin)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsDuplicateNameIgnoringCase() throws Exception {
		mvc.perform(json(post("/api/domains"), "{\"name\":\"EJEMPLO.com\"}").with(as(admin)))
				.andExpect(status().isConflict());
	}

	@Test
	void cannotRenameDomainWithSubdomains() throws Exception {
		mvc.perform(json(put("/api/domains/{id}", root.getId()), "{\"name\":\"nuevo.com\"}").with(as(admin)))
				.andExpect(status().isConflict());
	}

	@Test
	void onlyAdminManagesDomains() throws Exception {
		mvc.perform(json(post("/api/domains"), "{\"name\":\"nuevo.com\"}").with(as(member)))
				.andExpect(status().isForbidden());
		mvc.perform(delete("/api/domains/{id}", app.getId()).with(as(member))).andExpect(status().isForbidden());
	}

	@Test
	void deletingDomainDeletesSubdomains() throws Exception {
		mvc.perform(delete("/api/domains/{id}", root.getId()).with(as(admin))).andExpect(status().isNoContent());
		assertThat(domains.findByWorkspaceIdOrderByName(workspace.getId())).isEmpty();
	}

	private Domain newDomain(String name, Domain parent, Project project) {
		Domain domain = new Domain(workspace);
		domain.setName(name);
		domain.setParent(parent);
		domain.setProject(project);
		return domains.save(domain);
	}

	private RequestPostProcessor as(AppUser appUser) {
		return request -> csrf().postProcessRequest(
				user(userDetailsService.loadUserByUsername(appUser.getEmail())).postProcessRequest(request));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
