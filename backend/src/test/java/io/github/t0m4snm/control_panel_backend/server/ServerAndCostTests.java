package io.github.t0m4snm.control_panel_backend.server;

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
import io.github.t0m4snm.control_panel_backend.contract.ContractStatus;
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
 * Contratos, servidores, reparto de costes y resumen. Escenario:
 * <ul>
 * <li>VPS IONOS, 10 €/mes, repartido 60 % proyecto A y 40 % proyecto B.</li>
 * <li>VPS Contabo, 60 €/año (5 €/mes), 100 % proyecto B.</li>
 * <li>Dominio, 12 €/año (1 €/mes), sin proyecto.</li>
 * <li>Un contrato cancelado de 100 €/mes, que no cuenta.</li>
 * <li>El miembro es {@code VIEWER} del proyecto A y no ve nada de B ni de dinero.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ServerAndCostTests {

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
	private ServerRepository servers;

	@Autowired
	private ProjectServerRepository links;

	@Autowired
	private UserDetailsService userDetailsService;

	private Workspace workspace;

	private AppUser admin;

	private AppUser member;

	private AppUser outsider;

	private Project projectA;

	private Project projectB;

	private Contract ionosContract;

	private Server ionos;

	private Server contabo;

	@BeforeEach
	void setUp() {
		workspace = workspaces.save(new Workspace("Principal"));
		admin = newUser("admin@example.com", UserRole.ADMIN);
		member = newUser("member@example.com", UserRole.MEMBER);
		outsider = newUser("outsider@example.com", UserRole.MEMBER);
		projectA = projects.save(new Project(workspace, "A"));
		projectB = projects.save(new Project(workspace, "B"));
		members.save(new ProjectMember(projectA, member, ProjectRole.VIEWER));

		ionosContract = newContract("VPS IONOS", ContractType.SERVER, "10.00", BillingPeriod.MONTHLY);
		Contract contaboContract = newContract("VPS Contabo", ContractType.SERVER, "60.00", BillingPeriod.YEARLY);
		newContract("ejemplo.com", ContractType.DOMAIN, "12.00", BillingPeriod.YEARLY);
		Contract cancelled = newContract("Antiguo", ContractType.OTHER, "100.00", BillingPeriod.MONTHLY);
		cancelled.setStatus(ContractStatus.CANCELLED);

		ionos = newServer("IONOS", ionosContract);
		contabo = newServer("Contabo", contaboContract);
		links.save(new ProjectServer(projectA, ionos, new BigDecimal("60.00")));
		links.save(new ProjectServer(projectB, ionos, new BigDecimal("40.00")));
		links.save(new ProjectServer(projectB, contabo, new BigDecimal("100.00")));
	}

	@Test
	void contractsAndCostsAreAdminOnly() throws Exception {
		mvc.perform(get("/api/contracts").with(as(member))).andExpect(status().isForbidden());
		mvc.perform(get("/api/contracts/{id}", ionosContract.getId()).with(as(member)))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/costs/summary").with(as(member))).andExpect(status().isForbidden());
		mvc.perform(get("/api/contracts").with(as(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(4));
	}

	@Test
	void createsContractWithMonthlyEquivalent() throws Exception {
		mvc.perform(json(post("/api/contracts"), """
				{"name":"Correo","provider":"Proton","type":"OTHER","amount":100.00,
				 "billingPeriod":"YEARLY","renewalDate":"2027-03-01"}
				""").with(as(admin)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.monthlyAmount").value(8.33))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.autoRenew").value(true));
	}

	@Test
	void validatesContractAmount() throws Exception {
		String template = """
				{"name":"X","provider":"Y","type":"OTHER","amount":%s,"billingPeriod":"MONTHLY","renewalDate":"2027-01-01"}
				""";
		mvc.perform(json(post("/api/contracts"), template.formatted("-1")).with(as(admin)))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/contracts"), template.formatted("1.999")).with(as(admin)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void memberSeesOnlyServersOfHisProjectsWithoutMoney() throws Exception {
		mvc.perform(get("/api/servers").with(as(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("IONOS"))
				.andExpect(jsonPath("$[0].costs").doesNotExist())
				.andExpect(jsonPath("$[0].projects.length()").value(1))
				.andExpect(jsonPath("$[0].projects[0].projectName").value("A"))
				.andExpect(jsonPath("$[0].projects[0].costSharePercent").doesNotExist())
				.andExpect(jsonPath("$[0].projects[0].monthlyCost").doesNotExist());

		mvc.perform(get("/api/servers/{id}", ionos.getId()).with(as(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.costs").doesNotExist());
		mvc.perform(get("/api/servers/{id}", contabo.getId()).with(as(member))).andExpect(status().isNotFound());
		mvc.perform(get("/api/servers").with(as(outsider))).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void onlyAdminManagesServers() throws Exception {
		mvc.perform(json(post("/api/servers"), "{\"name\":\"Nuevo\"}").with(as(member)))
				.andExpect(status().isForbidden());
		mvc.perform(json(put("/api/servers/{id}", ionos.getId()), "{\"name\":\"X\"}").with(as(member)))
				.andExpect(status().isForbidden());
		mvc.perform(json(put("/api/projects/{p}/servers/{s}", projectA.getId(), contabo.getId()),
				"{\"costSharePercent\":0}").with(as(member)))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminSeesServerCostsAndShares() throws Exception {
		mvc.perform(get("/api/servers/{id}", ionos.getId()).with(as(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.costs.contractName").value("VPS IONOS"))
				.andExpect(jsonPath("$.costs.monthlyAmount").value(10.00))
				.andExpect(jsonPath("$.costs.assignedSharePercent").value(100.00))
				.andExpect(jsonPath("$.projects[0].projectName").value("A"))
				.andExpect(jsonPath("$.projects[0].monthlyCost").value(6.00))
				.andExpect(jsonPath("$.projects[1].monthlyCost").value(4.00));
	}

	@Test
	void sharesOfAServerCannotExceed100() throws Exception {
		mvc.perform(json(put("/api/projects/{p}/servers/{s}", projectA.getId(), ionos.getId()),
				"{\"costSharePercent\":70}").with(as(admin)))
				.andExpect(status().isConflict());

		mvc.perform(json(put("/api/projects/{p}/servers/{s}", projectA.getId(), ionos.getId()),
				"{\"costSharePercent\":50}").with(as(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.costs.assignedSharePercent").value(90.00));

		mvc.perform(json(put("/api/projects/{p}/servers/{s}", projectA.getId(), contabo.getId()),
				"{\"costSharePercent\":1}").with(as(admin)))
				.andExpect(status().isConflict());
	}

	@Test
	void costSummaryUsesActiveContractsAndShares() throws Exception {
		mvc.perform(get("/api/costs/summary").with(as(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalMonthly").value(16.00))
				.andExpect(jsonPath("$.totalYearly").value(192.00))
				.andExpect(jsonPath("$.byType.SERVER").value(15.00))
				.andExpect(jsonPath("$.byType.DOMAIN").value(1.00))
				.andExpect(jsonPath("$.byType.OTHER").value(0.00))
				.andExpect(jsonPath("$.projects[0].projectName").value("A"))
				.andExpect(jsonPath("$.projects[0].monthlyCost").value(6.00))
				.andExpect(jsonPath("$.projects[1].projectName").value("B"))
				.andExpect(jsonPath("$.projects[1].monthlyCost").value(9.00))
				.andExpect(jsonPath("$.unassignedMonthly").value(1.00));
	}

	@Test
	void projectServersFollowProjectVisibility() throws Exception {
		mvc.perform(get("/api/projects/{id}/servers", projectA.getId()).with(as(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("IONOS"));
		mvc.perform(get("/api/projects/{id}/servers", projectB.getId()).with(as(member)))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/projects/{id}/servers", projectB.getId()).with(as(admin)))
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void cannotLinkContractFromAnotherWorkspace() throws Exception {
		Workspace other = workspaces.save(new Workspace("Otro"));
		Contract foreign = new Contract(other);
		foreign.setName("Ajeno");
		foreign.setProvider("X");
		foreign.setType(ContractType.SERVER);
		foreign.setAmount(BigDecimal.ONE);
		foreign.setBillingPeriod(BillingPeriod.MONTHLY);
		foreign.setRenewalDate(LocalDate.now());
		contracts.save(foreign);

		mvc.perform(json(post("/api/servers"), "{\"name\":\"X\",\"contractId\":\"" + foreign.getId() + "\"}")
				.with(as(admin)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void deletingContractUnlinksServer() throws Exception {
		mvc.perform(delete("/api/contracts/{id}", ionosContract.getId()).with(as(admin)))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/servers/{id}", ionos.getId()).with(as(admin)))
				.andExpect(jsonPath("$.costs.contractId").doesNotExist())
				.andExpect(jsonPath("$.costs.monthlyAmount").value(0.00));
	}

	@Test
	void deletingServerRemovesItsLinks() throws Exception {
		mvc.perform(delete("/api/servers/{id}", ionos.getId()).with(as(admin))).andExpect(status().isNoContent());
		assertThat(links.findByServerId(ionos.getId())).isEmpty();
		mvc.perform(get("/api/servers").with(as(member))).andExpect(jsonPath("$.length()").value(0));
	}

	private AppUser newUser(String email, UserRole role) {
		return users.save(new AppUser(workspace, email, "{noop}x", email, role));
	}

	private Contract newContract(String name, ContractType type, String amount, BillingPeriod period) {
		Contract contract = new Contract(workspace);
		contract.setName(name);
		contract.setProvider("Proveedor");
		contract.setType(type);
		contract.setAmount(new BigDecimal(amount));
		contract.setBillingPeriod(period);
		contract.setRenewalDate(LocalDate.now().plusMonths(6));
		return contracts.save(contract);
	}

	private Server newServer(String name, Contract contract) {
		Server server = new Server(workspace);
		server.setName(name);
		server.setContract(contract);
		return servers.save(server);
	}

	private RequestPostProcessor as(AppUser appUser) {
		return request -> csrf().postProcessRequest(
				user(userDetailsService.loadUserByUsername(appUser.getEmail())).postProcessRequest(request));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
