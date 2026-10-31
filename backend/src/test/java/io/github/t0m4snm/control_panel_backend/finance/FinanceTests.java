package io.github.t0m4snm.control_panel_backend.finance;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
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
import io.github.t0m4snm.control_panel_backend.movement.Movement;
import io.github.t0m4snm.control_panel_backend.movement.MovementRepository;
import io.github.t0m4snm.control_panel_backend.movement.MovementType;
import io.github.t0m4snm.control_panel_backend.movement.Recurrence;
import io.github.t0m4snm.control_panel_backend.project.Project;
import io.github.t0m4snm.control_panel_backend.project.ProjectMember;
import io.github.t0m4snm.control_panel_backend.project.ProjectMemberRepository;
import io.github.t0m4snm.control_panel_backend.project.ProjectRepository;
import io.github.t0m4snm.control_panel_backend.project.ProjectRole;
import io.github.t0m4snm.control_panel_backend.server.ProjectServer;
import io.github.t0m4snm.control_panel_backend.server.ProjectServerRepository;
import io.github.t0m4snm.control_panel_backend.server.Server;
import io.github.t0m4snm.control_panel_backend.server.ServerRepository;
import io.github.t0m4snm.control_panel_backend.timetracking.TimeEntry;
import io.github.t0m4snm.control_panel_backend.timetracking.TimeEntryRepository;
import io.github.t0m4snm.control_panel_backend.user.AppUser;
import io.github.t0m4snm.control_panel_backend.user.AppUserRepository;
import io.github.t0m4snm.control_panel_backend.user.UserRole;
import io.github.t0m4snm.control_panel_backend.user.Workspace;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/**
 * Movimientos y resumen económico de 2020 (año bisiesto). Escenario:
 * <ul>
 * <li>Contratos: VPS de 10 €/mes, 100 % del proyecto A; dominio de 12 €/año sin proyecto. Total 11 €/mes.</li>
 * <li>Ingreso mensual de 50 € del proyecto A, día 31, de enero a junio: 6 cobros (en febrero, el 29).</li>
 * <li>Gasto puntual de 20 € del proyecto A en marzo.</li>
 * <li>Ingreso anual de 120 € del proyecto B que empezó en mayo de 2019: cuenta en mayo de 2020.</li>
 * <li>Gasto puntual de 30 € sin proyecto en febrero.</li>
 * <li>Ingreso puntual de 999 € en diciembre de 2019: fuera del periodo.</li>
 * <li>Tiempo: 2 h 30 min del proyecto A en marzo a 40 €/h (100 €) y una entrada de diciembre de
 * 2019, fuera del periodo.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class FinanceTests {

	private static final String YEAR_2020 = "/api/finance/summary?from=2020-01&to=2020-12";

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
	private MovementRepository movements;

	@Autowired
	private TimeEntryRepository timeEntries;

	@Autowired
	private UserDetailsService userDetailsService;

	private Workspace workspace;

	private AppUser admin;

	private AppUser member;

	private Project projectA;

	private Project projectB;

	@BeforeEach
	void setUp() {
		workspace = workspaces.save(new Workspace("Principal"));
		admin = users.save(new AppUser(workspace, "admin@example.com", "{noop}x", "Admin", UserRole.ADMIN));
		member = users.save(new AppUser(workspace, "member@example.com", "{noop}x", "Member", UserRole.MEMBER));
		projectA = projects.save(new Project(workspace, "A"));
		projectB = projects.save(new Project(workspace, "B"));
		members.save(new ProjectMember(projectA, member, ProjectRole.OWNER));

		Server server = new Server(workspace);
		server.setName("VPS");
		server.setContract(newContract("VPS", ContractType.SERVER, "10.00", BillingPeriod.MONTHLY));
		servers.save(server);
		links.save(new ProjectServer(projectA, server, new BigDecimal("100")));
		newContract("Dominio", ContractType.DOMAIN, "12.00", BillingPeriod.YEARLY);

		Movement monthlyIncome = newMovement(MovementType.INCOME, "50.00", LocalDate.of(2020, 1, 31), projectA,
				Recurrence.MONTHLY);
		monthlyIncome.setEndsOn(LocalDate.of(2020, 6, 30));
		newMovement(MovementType.EXPENSE, "20.00", LocalDate.of(2020, 3, 15), projectA, Recurrence.ONE_TIME);
		newMovement(MovementType.INCOME, "120.00", LocalDate.of(2019, 5, 10), projectB, Recurrence.YEARLY);
		newMovement(MovementType.EXPENSE, "30.00", LocalDate.of(2020, 2, 1), null, Recurrence.ONE_TIME);
		newMovement(MovementType.INCOME, "999.00", LocalDate.of(2019, 12, 31), projectA, Recurrence.ONE_TIME);

		newTimeEntry(projectA, "2020-03-10T09:00:00Z", "2020-03-10T11:30:00Z");
		newTimeEntry(projectB, "2019-12-10T09:00:00Z", "2019-12-10T10:00:00Z");
	}

	@Test
	void timeCostsReduceRealProfit() throws Exception {
		mvc.perform(get(YEAR_2020).with(as(admin)))
				.andExpect(jsonPath("$.totals.timeCosts").value(100.00))
				.andExpect(jsonPath("$.totals.profit").value(238.00))
				.andExpect(jsonPath("$.totals.realProfit").value(138.00))
				.andExpect(jsonPath("$.months[2].timeCosts").value(100.00))
				.andExpect(jsonPath("$.months[3].timeCosts").value(0))
				.andExpect(jsonPath("$.projects[0].timeCosts").value(100.00))
				.andExpect(jsonPath("$.projects[0].realProfit").value(60.00))
				.andExpect(jsonPath("$.projects[1].timeCosts").value(0))
				.andExpect(jsonPath("$.unassigned.timeCosts").value(0));
	}

	@Test
	void movementsAndFinanceAreAdminOnly() throws Exception {
		mvc.perform(get("/api/movements").with(as(member))).andExpect(status().isForbidden());
		mvc.perform(get(YEAR_2020).with(as(member))).andExpect(status().isForbidden());
		mvc.perform(json(post("/api/movements"),
				"{\"type\":\"INCOME\",\"amount\":1,\"date\":\"2020-01-01\",\"description\":\"x\"}").with(as(member)))
				.andExpect(status().isForbidden());
	}

	@Test
	void yearTotalsAddUp() throws Exception {
		mvc.perform(get(YEAR_2020).with(as(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.from").value("2020-01"))
				.andExpect(jsonPath("$.months.length()").value(12))
				.andExpect(jsonPath("$.totals.income").value(420.00))
				.andExpect(jsonPath("$.totals.expenses").value(50.00))
				.andExpect(jsonPath("$.totals.contractCosts").value(132.00))
				.andExpect(jsonPath("$.totals.profit").value(238.00));
	}

	@Test
	void recurringMovementsCountInTheRightMonths() throws Exception {
		mvc.perform(get(YEAR_2020).with(as(admin)))
				.andExpect(jsonPath("$.months[0].income").value(50.00))
				.andExpect(jsonPath("$.months[1].income").value(50.00))
				.andExpect(jsonPath("$.months[1].expenses").value(30.00))
				.andExpect(jsonPath("$.months[2].expenses").value(20.00))
				.andExpect(jsonPath("$.months[4].income").value(170.00))
				.andExpect(jsonPath("$.months[5].income").value(50.00))
				.andExpect(jsonPath("$.months[6].income").value(0))
				.andExpect(jsonPath("$.months[0].contractCosts").value(11.00))
				.andExpect(jsonPath("$.months[0].profit").value(39.00))
				.andExpect(jsonPath("$.months[0].projected").value(false));
	}

	@Test
	void profitPerProjectAndUnassigned() throws Exception {
		mvc.perform(get(YEAR_2020).with(as(admin)))
				.andExpect(jsonPath("$.projects[0].projectName").value("A"))
				.andExpect(jsonPath("$.projects[0].income").value(300.00))
				.andExpect(jsonPath("$.projects[0].expenses").value(20.00))
				.andExpect(jsonPath("$.projects[0].contractCosts").value(120.00))
				.andExpect(jsonPath("$.projects[0].profit").value(160.00))
				.andExpect(jsonPath("$.projects[1].projectName").value("B"))
				.andExpect(jsonPath("$.projects[1].profit").value(120.00))
				.andExpect(jsonPath("$.unassigned.expenses").value(30.00))
				.andExpect(jsonPath("$.unassigned.contractCosts").value(12.00))
				.andExpect(jsonPath("$.unassigned.profit").value(-42.00));
	}

	@Test
	void futureMonthsAreProjected() throws Exception {
		mvc.perform(get("/api/finance/summary?from=2099-01&to=2099-02").with(as(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.months[0].projected").value(true))
				.andExpect(jsonPath("$.months[0].contractCosts").value(11.00));
	}

	@Test
	void validatesPeriod() throws Exception {
		mvc.perform(get("/api/finance/summary?from=2020-06&to=2020-01").with(as(admin)))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/finance/summary?from=2020-01&to=2023-01").with(as(admin)))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/finance/summary?from=enero").with(as(admin)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createsAndFiltersMovements() throws Exception {
		mvc.perform(json(post("/api/movements"), """
				{"type":"EXPENSE","amount":9.99,"date":"2020-04-01","description":" Licencia ",
				 "projectId":"%s","recurrence":"YEARLY","endsOn":"2022-04-01"}
				""".formatted(projectB.getId())).with(as(admin)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.description").value("Licencia"))
				.andExpect(jsonPath("$.project.name").value("B"))
				.andExpect(jsonPath("$.recurrence").value("YEARLY"));

		mvc.perform(get("/api/movements").param("projectId", projectB.getId().toString()).with(as(admin)))
				.andExpect(jsonPath("$.length()").value(2));
		mvc.perform(get("/api/movements").with(as(admin)))
				.andExpect(jsonPath("$.length()").value(6))
				.andExpect(jsonPath("$[0].date").value("2020-04-01"));
	}

	@Test
	void validatesMovements() throws Exception {
		String base = "{\"type\":\"INCOME\",\"amount\":%s,\"date\":\"2020-05-01\",\"description\":\"x\"%s}";
		mvc.perform(json(post("/api/movements"), base.formatted("0", "")).with(as(admin)))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/movements"), base.formatted("10", ",\"endsOn\":\"2020-06-01\"")).with(as(admin)))
				.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/movements"),
				base.formatted("10", ",\"recurrence\":\"MONTHLY\",\"endsOn\":\"2020-04-01\"")).with(as(admin)))
				.andExpect(status().isBadRequest());
	}

	private Contract newContract(String name, ContractType type, String amount, BillingPeriod period) {
		Contract contract = new Contract(workspace);
		contract.setName(name);
		contract.setProvider("Proveedor");
		contract.setType(type);
		contract.setAmount(new BigDecimal(amount));
		contract.setBillingPeriod(period);
		contract.setRenewalDate(LocalDate.of(2030, 1, 1));
		return contracts.save(contract);
	}

	private Movement newMovement(MovementType type, String amount, LocalDate date, Project project,
			Recurrence recurrence) {
		Movement movement = new Movement(workspace);
		movement.setType(type);
		movement.setAmount(new BigDecimal(amount));
		movement.setDate(date);
		movement.setDescription(type + " " + amount);
		movement.setProject(project);
		movement.setRecurrence(recurrence);
		return movements.save(movement);
	}

	private void newTimeEntry(Project project, String startedAt, String endedAt) {
		TimeEntry entry = new TimeEntry(admin, project, Instant.parse(startedAt));
		entry.setEndedAt(Instant.parse(endedAt));
		entry.setHourlyRate(new BigDecimal("40.00"));
		timeEntries.save(entry);
	}

	private RequestPostProcessor as(AppUser appUser) {
		return request -> csrf().postProcessRequest(
				user(userDetailsService.loadUserByUsername(appUser.getEmail())).postProcessRequest(request));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
