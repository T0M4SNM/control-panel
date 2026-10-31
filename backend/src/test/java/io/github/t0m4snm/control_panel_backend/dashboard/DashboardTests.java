package io.github.t0m4snm.control_panel_backend.dashboard;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.TestcontainersConfiguration;
import io.github.t0m4snm.control_panel_backend.contract.BillingPeriod;
import io.github.t0m4snm.control_panel_backend.contract.Contract;
import io.github.t0m4snm.control_panel_backend.contract.ContractRepository;
import io.github.t0m4snm.control_panel_backend.contract.ContractStatus;
import io.github.t0m4snm.control_panel_backend.contract.ContractType;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckResult;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckResultRepository;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckType;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheck;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheckRepository;
import io.github.t0m4snm.control_panel_backend.project.Project;
import io.github.t0m4snm.control_panel_backend.project.ProjectMember;
import io.github.t0m4snm.control_panel_backend.project.ProjectMemberRepository;
import io.github.t0m4snm.control_panel_backend.project.ProjectRepository;
import io.github.t0m4snm.control_panel_backend.project.ProjectRole;
import io.github.t0m4snm.control_panel_backend.project.ProjectStatus;
import io.github.t0m4snm.control_panel_backend.timetracking.TimeEntry;
import io.github.t0m4snm.control_panel_backend.timetracking.TimeEntryRepository;
import io.github.t0m4snm.control_panel_backend.user.AppUser;
import io.github.t0m4snm.control_panel_backend.user.AppUserRepository;
import io.github.t0m4snm.control_panel_backend.user.UserRole;
import io.github.t0m4snm.control_panel_backend.user.Workspace;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/**
 * Proyecto A (en producción, del miembro) y B (en desarrollo). Comprobaciones: web de A caída, SSL
 * de A que caduca en 20 días, web de B activa y una desactivada. Contratos que vencen en 10, 90 y
 * -5 días, más uno cancelado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DashboardTests {

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
	private HealthCheckRepository checks;

	@Autowired
	private CheckResultRepository results;

	@Autowired
	private ContractRepository contracts;

	@Autowired
	private TimeEntryRepository timeEntries;

	@Autowired
	private UserDetailsService userDetailsService;

	private Workspace workspace;

	private AppUser admin;

	private AppUser member;

	@BeforeEach
	void setUp() {
		workspace = workspaces.save(new Workspace("Principal"));
		admin = users.save(new AppUser(workspace, "admin@example.com", "{noop}x", "Admin", UserRole.ADMIN));
		member = users.save(new AppUser(workspace, "member@example.com", "{noop}x", "Member", UserRole.MEMBER));
		Project projectA = new Project(workspace, "A");
		projectA.setStatus(ProjectStatus.PRODUCTION);
		projects.save(projectA);
		Project projectB = new Project(workspace, "B");
		projectB.setStatus(ProjectStatus.DEVELOPMENT);
		projects.save(projectB);
		members.save(new ProjectMember(projectA, member, ProjectRole.EDITOR));

		HealthCheck webA = newCheck("Web A", CheckType.HTTP, "https://a.ejemplo.com", projectA);
		webA.setFailureThreshold(1);
		webA.record(false, Instant.now().minus(10, ChronoUnit.MINUTES));
		HealthCheck sslA = newCheck("SSL A", CheckType.SSL, "a.ejemplo.com", projectA);
		sslA.record(true, Instant.now());
		results.save(new CheckResult(sslA, Instant.now(), true, 40, null, null, Instant.now().plus(20, ChronoUnit.DAYS)));
		newCheck("Web B", CheckType.HTTP, "https://b.ejemplo.com", projectB).record(true, Instant.now());
		newCheck("Desactivada", CheckType.PING, "10.0.0.1", projectB).setEnabled(false);

		newContract("VPS IONOS", 10);
		newContract("Lejano", 90);
		newContract("Vencido", -5);
		newContract("Cancelado", 3).setStatus(ContractStatus.CANCELLED);

		TimeEntry running = new TimeEntry(admin, projectB, Instant.now().minus(30, ChronoUnit.MINUTES));
		timeEntries.save(running);
	}

	@Test
	void adminSeesEverythingIncludingMoney() throws Exception {
		mvc.perform(get("/api/dashboard").with(as(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.projects.total").value(2))
				.andExpect(jsonPath("$.projects.byStatus.PRODUCTION").value(1))
				.andExpect(jsonPath("$.projects.byStatus.PAUSED").value(0))
				.andExpect(jsonPath("$.checks.total").value(3))
				.andExpect(jsonPath("$.checks.byStatus.DOWN").value(1))
				.andExpect(jsonPath("$.checks.byStatus.UP").value(2))
				.andExpect(jsonPath("$.checks.down[0].name").value("Web A"))
				.andExpect(jsonPath("$.sslExpiring[0].name").value("SSL A"))
				.andExpect(jsonPath("$.sslExpiring[0].daysLeft").value(20))
				.andExpect(jsonPath("$.renewals.length()").value(2))
				.andExpect(jsonPath("$.renewals[0].name").value("Vencido"))
				.andExpect(jsonPath("$.renewals[0].daysLeft").value(-5))
				.andExpect(jsonPath("$.renewals[1].name").value("VPS IONOS"))
				.andExpect(jsonPath("$.costs.totalMonthly").value(18.00))
				.andExpect(jsonPath("$.thisMonth.contractCosts").value(18.00))
				.andExpect(jsonPath("$.runningTimer.project.name").value("B"))
				.andExpect(jsonPath("$.unreadNotifications").value(0));
	}

	@Test
	void memberSeesOnlyHisProjectsAndNoMoney() throws Exception {
		mvc.perform(get("/api/dashboard").with(as(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.projects.total").value(1))
				.andExpect(jsonPath("$.checks.total").value(2))
				.andExpect(jsonPath("$.checks.down.length()").value(1))
				.andExpect(jsonPath("$.sslExpiring.length()").value(1))
				.andExpect(jsonPath("$.renewals").doesNotExist())
				.andExpect(jsonPath("$.costs").doesNotExist())
				.andExpect(jsonPath("$.thisMonth").doesNotExist())
				.andExpect(jsonPath("$.runningTimer").doesNotExist());
	}

	private HealthCheck newCheck(String name, CheckType type, String target, Project project) {
		HealthCheck check = new HealthCheck(workspace);
		check.setName(name);
		check.setType(type);
		check.setTarget(target);
		check.setProject(project);
		return checks.save(check);
	}

	private Contract newContract(String name, int daysLeft) {
		Contract contract = new Contract(workspace);
		contract.setName(name);
		contract.setProvider("IONOS");
		contract.setType(ContractType.SERVER);
		contract.setAmount(new BigDecimal("6.00"));
		contract.setBillingPeriod(BillingPeriod.MONTHLY);
		contract.setRenewalDate(LocalDate.now().plusDays(daysLeft));
		return contracts.save(contract);
	}

	private RequestPostProcessor as(AppUser appUser) {
		return user(userDetailsService.loadUserByUsername(appUser.getEmail()));
	}

}
