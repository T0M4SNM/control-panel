package io.github.t0m4snm.control_panel_backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
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
import io.github.t0m4snm.control_panel_backend.domain.Domain;
import io.github.t0m4snm.control_panel_backend.domain.DomainRepository;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckResult;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckResultRepository;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckStatus;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckType;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheck;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheckRepository;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheckStatusChanged;
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
 * Avisos de vencimiento, de caídas y el panel. El {@code member} es del proyecto A; el
 * {@code outsider}, solo del B. La comprobación web y la SSL son del proyecto A (la SSL, a través de
 * su dominio).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NotificationTests {

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
	private HealthCheckRepository checks;

	@Autowired
	private CheckResultRepository results;

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private ExpiryNotifier expiryNotifier;

	@Autowired
	private ApplicationEventPublisher events;

	@Autowired
	private UserDetailsService userDetailsService;

	private Workspace workspace;

	private AppUser admin;

	private AppUser inactiveAdmin;

	private AppUser member;

	private AppUser outsider;

	private HealthCheck web;

	private HealthCheck ssl;

	@BeforeEach
	void setUp() {
		workspace = workspaces.save(new Workspace("Principal"));
		admin = newUser("admin@example.com", UserRole.ADMIN);
		inactiveAdmin = newUser("old-admin@example.com", UserRole.ADMIN);
		inactiveAdmin.setActive(false);
		member = newUser("member@example.com", UserRole.MEMBER);
		outsider = newUser("outsider@example.com", UserRole.MEMBER);
		Project projectA = projects.save(new Project(workspace, "A"));
		Project projectB = projects.save(new Project(workspace, "B"));
		members.save(new ProjectMember(projectA, member, ProjectRole.VIEWER));
		members.save(new ProjectMember(projectB, outsider, ProjectRole.OWNER));

		web = newCheck("Web A", CheckType.HTTP, "https://a.ejemplo.com");
		web.setProject(projectA);
		Domain domain = new Domain(workspace);
		domain.setName("a.ejemplo.com");
		domain.setProject(projectA);
		domains.save(domain);
		ssl = newCheck("SSL A", CheckType.SSL, "a.ejemplo.com");
		ssl.setDomain(domain);
		// El aviso se escribe con SQL directo: lo creado con JPA tiene que estar ya en la base
		users.flush();
	}

	@Test
	void contractAlertsGoToActiveAdminsAtTheClosestThreshold() {
		Contract soon = newContract("VPS IONOS", 10);
		Contract urgent = newContract("Dominio", 5);
		newContract("Lejano", 40);
		newContract("Vencido", -1);
		newContract("Cancelado", 3).setStatus(ContractStatus.CANCELLED);
		contracts.flush();

		expiryNotifier.run();

		List<Notification> adminNotifications = notifications.findByUserId(admin.getId());
		assertThat(adminNotifications).extracting(Notification::getDedupeKey).containsExactlyInAnyOrder(
				"contract:%s:15d:%s".formatted(soon.getId(), soon.getRenewalDate()),
				"contract:%s:7d:%s".formatted(urgent.getId(), urgent.getRenewalDate()));
		assertThat(adminNotifications).filteredOn(n -> n.getContractId().equals(urgent.getId()))
				.singleElement()
				.satisfies(n -> {
					assertThat(n.getSeverity()).isEqualTo(Severity.CRITICAL);
					assertThat(n.getMessage()).contains("dentro de 5 días").contains("6,00").contains("al mes");
				});
		assertThat(notifications.findByUserId(inactiveAdmin.getId())).isEmpty();
		assertThat(notifications.findByUserId(member.getId())).isEmpty();
	}

	@Test
	void runningTwiceDoesNotRepeatAlerts() {
		newContract("VPS IONOS", 10);
		contracts.flush();

		assertThat(expiryNotifier.run()).isEqualTo(1);
		assertThat(expiryNotifier.run()).isZero();
		assertThat(notifications.findByUserId(admin.getId())).hasSize(1);
	}

	@Test
	void sslAlertsGoToAdminsAndMembersOfAffectedProjects() {
		results.save(new CheckResult(ssl, Instant.now(), true, 50, null, null,
				Instant.now().plus(20, ChronoUnit.DAYS)));
		results.flush();

		expiryNotifier.run();

		assertThat(notifications.findByUserId(admin.getId())).singleElement()
				.satisfies(n -> {
					assertThat(n.getType()).isEqualTo(NotificationType.SSL_EXPIRING);
					assertThat(n.getDedupeKey()).startsWith("ssl:%s:30d:".formatted(ssl.getId()));
				});
		assertThat(notifications.findByUserId(member.getId())).hasSize(1);
		assertThat(notifications.findByUserId(outsider.getId())).isEmpty();
	}

	@Test
	void downAndRecoveryAlertTheProjectButNotTheFirstSuccess() {
		Instant t0 = Instant.parse("2026-10-31T10:00:00Z");
		events.publishEvent(new HealthCheckStatusChanged(web.getId(), CheckStatus.UNKNOWN, CheckStatus.UP, t0));
		assertThat(notifications.findByUserId(admin.getId())).isEmpty();

		events.publishEvent(new HealthCheckStatusChanged(web.getId(), CheckStatus.UP, CheckStatus.DOWN,
				t0.plusSeconds(900)));
		events.publishEvent(new HealthCheckStatusChanged(web.getId(), CheckStatus.DOWN, CheckStatus.UP,
				t0.plusSeconds(1800)));

		assertThat(notifications.findByUserId(member.getId())).extracting(Notification::getType)
				.containsExactlyInAnyOrder(NotificationType.CHECK_DOWN, NotificationType.CHECK_RECOVERED);
		assertThat(notifications.findByUserId(admin.getId())).hasSize(2);
		assertThat(notifications.findByUserId(outsider.getId())).isEmpty();
	}

	@Test
	void panelShowsOnlyOwnNotificationsAndMarksThemRead() throws Exception {
		events.publishEvent(new HealthCheckStatusChanged(web.getId(), CheckStatus.UP, CheckStatus.DOWN,
				Instant.parse("2026-10-31T10:00:00Z")));
		events.publishEvent(new HealthCheckStatusChanged(web.getId(), CheckStatus.DOWN, CheckStatus.UP,
				Instant.parse("2026-10-31T11:00:00Z")));
		Notification adminOne = notifications.findByUserId(admin.getId()).getFirst();

		mvc.perform(get("/api/notifications/unread-count").with(as(member)))
				.andExpect(jsonPath("$.count").value(2));
		mvc.perform(get("/api/notifications").with(as(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].type").value("CHECK_RECOVERED"))
				.andExpect(jsonPath("$[1].title").value("Caída: Web A"));

		String id = notifications.findByUserId(member.getId()).getFirst().getId().toString();
		mvc.perform(post("/api/notifications/{id}/read", id).with(as(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.readAt").exists());
		mvc.perform(get("/api/notifications").param("unreadOnly", "true").with(as(member)))
				.andExpect(jsonPath("$.length()").value(1));

		mvc.perform(post("/api/notifications/{id}/read", adminOne.getId()).with(as(member)))
				.andExpect(status().isNotFound());

		mvc.perform(post("/api/notifications/read-all").with(as(member))).andExpect(status().isOk());
		mvc.perform(get("/api/notifications/unread-count").with(as(member)))
				.andExpect(jsonPath("$.count").value(0));
		mvc.perform(get("/api/notifications/unread-count").with(as(admin)))
				.andExpect(jsonPath("$.count").value(2));
	}

	@Test
	void thresholds() {
		assertThat(ExpiryNotifier.thresholdFor(31)).isEmpty();
		assertThat(ExpiryNotifier.thresholdFor(30)).hasValue(30);
		assertThat(ExpiryNotifier.thresholdFor(16)).hasValue(30);
		assertThat(ExpiryNotifier.thresholdFor(15)).hasValue(15);
		assertThat(ExpiryNotifier.thresholdFor(7)).hasValue(7);
		assertThat(ExpiryNotifier.thresholdFor(0)).hasValue(7);
		assertThat(ExpiryNotifier.thresholdFor(-1)).isEmpty();
	}

	private AppUser newUser(String email, UserRole role) {
		return users.save(new AppUser(workspace, email, "{noop}x", email, role));
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

	private HealthCheck newCheck(String name, CheckType type, String target) {
		HealthCheck check = new HealthCheck(workspace);
		check.setName(name);
		check.setType(type);
		check.setTarget(target);
		return checks.save(check);
	}

	private RequestPostProcessor as(AppUser appUser) {
		return request -> csrf().postProcessRequest(
				user(userDetailsService.loadUserByUsername(appUser.getEmail())).postProcessRequest(request));
	}

}
