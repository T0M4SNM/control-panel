package io.github.t0m4snm.control_panel_backend.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.contract.ContractRepository;
import io.github.t0m4snm.control_panel_backend.contract.ContractStatus;
import io.github.t0m4snm.control_panel_backend.cost.CostService;
import io.github.t0m4snm.control_panel_backend.cost.CostSummaryResponse;
import io.github.t0m4snm.control_panel_backend.dashboard.DashboardResponse.Checks;
import io.github.t0m4snm.control_panel_backend.dashboard.DashboardResponse.Costs;
import io.github.t0m4snm.control_panel_backend.dashboard.DashboardResponse.DownCheck;
import io.github.t0m4snm.control_panel_backend.dashboard.DashboardResponse.Month;
import io.github.t0m4snm.control_panel_backend.dashboard.DashboardResponse.Projects;
import io.github.t0m4snm.control_panel_backend.dashboard.DashboardResponse.Renewal;
import io.github.t0m4snm.control_panel_backend.dashboard.DashboardResponse.SslExpiry;
import io.github.t0m4snm.control_panel_backend.finance.FinanceService;
import io.github.t0m4snm.control_panel_backend.finance.FinanceSummaryResponse;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckResultRepository;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckStatus;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckType;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheckResponse;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheckService;
import io.github.t0m4snm.control_panel_backend.notification.NotificationRepository;
import io.github.t0m4snm.control_panel_backend.project.ProjectResponse;
import io.github.t0m4snm.control_panel_backend.project.ProjectService;
import io.github.t0m4snm.control_panel_backend.project.ProjectStatus;
import io.github.t0m4snm.control_panel_backend.timetracking.TimeEntryService;

/**
 * Junta en una sola petición lo que la pantalla de inicio necesita. Reutiliza los servicios de cada
 * sección, así que aplica sus mismas reglas de visibilidad: un miembro solo ve sus proyectos y sus
 * comprobaciones, y nunca dinero.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

	/** Contratos que vencen en los próximos 60 días (o que vencieron hace menos de 30 y siguen activos). */
	static final int RENEWALS_AHEAD_DAYS = 60;

	static final int RENEWALS_OVERDUE_DAYS = 30;

	static final int SSL_AHEAD_DAYS = 30;

	private final ProjectService projects;

	private final HealthCheckService checks;

	private final CheckResultRepository results;

	private final ContractRepository contracts;

	private final CostService costs;

	private final FinanceService finance;

	private final NotificationRepository notifications;

	private final TimeEntryService timer;

	private final Clock clock;

	public DashboardService(ProjectService projects, HealthCheckService checks, CheckResultRepository results,
			ContractRepository contracts, CostService costs, FinanceService finance,
			NotificationRepository notifications, TimeEntryService timer, Clock clock) {
		this.projects = projects;
		this.checks = checks;
		this.results = results;
		this.contracts = contracts;
		this.costs = costs;
		this.finance = finance;
		this.notifications = notifications;
		this.timer = timer;
		this.clock = clock;
	}

	public DashboardResponse summary(AuthenticatedUser user) {
		LocalDate today = LocalDate.now(clock);
		List<HealthCheckResponse> visibleChecks = checks.list(user).stream().filter(HealthCheckResponse::enabled).toList();
		boolean admin = user.isAdmin();
		return new DashboardResponse(
				projects(user),
				checks(visibleChecks),
				sslExpiring(visibleChecks, today),
				notifications.countByUserIdAndReadAtIsNull(user.id()),
				timer.current(user).orElse(null),
				admin ? renewals(user, today) : null,
				admin ? costs(user) : null,
				admin ? thisMonth(user, YearMonth.from(today)) : null);
	}

	private Projects projects(AuthenticatedUser user) {
		List<ProjectResponse> visible = projects.list(user);
		return new Projects(visible.size(), countBy(visible, ProjectResponse::status, ProjectStatus.class));
	}

	private static Checks checks(List<HealthCheckResponse> visible) {
		List<DownCheck> down = visible.stream()
				.filter(c -> c.currentStatus() == CheckStatus.DOWN)
				.sorted(Comparator.comparing(HealthCheckResponse::statusChangedAt,
						Comparator.nullsLast(Comparator.naturalOrder())))
				.map(c -> new DownCheck(c.id(), c.name(), c.target(), c.statusChangedAt()))
				.toList();
		return new Checks(visible.size(), countBy(visible, HealthCheckResponse::currentStatus, CheckStatus.class), down);
	}

	private List<SslExpiry> sslExpiring(List<HealthCheckResponse> visible, LocalDate today) {
		return visible.stream()
				.filter(c -> c.type() == CheckType.SSL)
				.flatMap(c -> results.findFirstByHealthCheckIdAndSslExpiresAtIsNotNullOrderByCheckedAtDesc(c.id())
						.map(r -> new SslExpiry(c.id(), c.name(), c.target(), r.getSslExpiresAt(),
								ChronoUnit.DAYS.between(today, LocalDate.ofInstant(r.getSslExpiresAt(), clock.getZone()))))
						.stream())
				.filter(s -> s.daysLeft() <= SSL_AHEAD_DAYS)
				.sorted(Comparator.comparing(SslExpiry::expiresAt))
				.toList();
	}

	private List<Renewal> renewals(AuthenticatedUser user, LocalDate today) {
		return contracts.findByWorkspaceIdAndStatus(user.workspaceId(), ContractStatus.ACTIVE).stream()
				.map(c -> new Renewal(c.getId(), c.getName(), c.getProvider(), c.getType(), c.getRenewalDate(),
						ChronoUnit.DAYS.between(today, c.getRenewalDate()), c.getAmount(), c.getBillingPeriod(),
						c.isAutoRenew()))
				.filter(r -> r.daysLeft() <= RENEWALS_AHEAD_DAYS && r.daysLeft() >= -RENEWALS_OVERDUE_DAYS)
				.sorted(Comparator.comparing(Renewal::renewalDate))
				.toList();
	}

	private Costs costs(AuthenticatedUser user) {
		CostSummaryResponse summary = costs.summary(user);
		return new Costs(summary.totalMonthly(), summary.totalYearly());
	}

	private Month thisMonth(AuthenticatedUser user, YearMonth month) {
		FinanceSummaryResponse.Totals t = finance.summary(user, month, month).totals();
		return new Month(t.income(), t.expenses(), t.contractCosts(), t.timeCosts(), t.profit(), t.realProfit());
	}

	/** Cuenta por valor de un enum, incluyendo los valores con cero para que el frontend no tenga huecos. */
	private static <T, E extends Enum<E>> Map<E, Long> countBy(List<T> items, Function<T, E> key, Class<E> type) {
		Map<E, Long> counts = new EnumMap<>(type);
		for (E value : type.getEnumConstants()) {
			counts.put(value, 0L);
		}
		counts.putAll(items.stream().collect(Collectors.groupingBy(key, Collectors.counting())));
		return counts;
	}

}
