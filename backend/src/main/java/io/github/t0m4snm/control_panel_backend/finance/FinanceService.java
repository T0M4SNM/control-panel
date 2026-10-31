package io.github.t0m4snm.control_panel_backend.finance;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.common.Money;
import io.github.t0m4snm.control_panel_backend.cost.CostService;
import io.github.t0m4snm.control_panel_backend.cost.CostService.MonthlyCosts;
import io.github.t0m4snm.control_panel_backend.finance.FinanceSummaryResponse.Month;
import io.github.t0m4snm.control_panel_backend.finance.FinanceSummaryResponse.ProjectTotals;
import io.github.t0m4snm.control_panel_backend.finance.FinanceSummaryResponse.Totals;
import io.github.t0m4snm.control_panel_backend.movement.Movement;
import io.github.t0m4snm.control_panel_backend.movement.MovementRepository;
import io.github.t0m4snm.control_panel_backend.movement.MovementType;
import io.github.t0m4snm.control_panel_backend.project.Project;
import io.github.t0m4snm.control_panel_backend.project.ProjectRepository;
import io.github.t0m4snm.control_panel_backend.timetracking.TimeEntry;
import io.github.t0m4snm.control_panel_backend.timetracking.TimeEntryRepository;

/**
 * Ingresos, gastos, coste de contratos, coste del tiempo y beneficio por mes y por proyecto. Solo
 * {@code ADMIN}.
 * <ul>
 * <li>Los movimientos recurrentes cuentan en cada mes en que les toca.</li>
 * <li>El coste de contratos de cada mes es el coste mensual <em>actual</em>: los contratos no
 * guardan desde cuándo están activos, así que para meses pasados es una aproximación y para meses
 * futuros una previsión.</li>
 * <li>El tiempo cuenta en el mes en que empieza la entrada (hora de Madrid); una entrada en marcha
 * cuenta hasta ahora.</li>
 * </ul>
 * Todo se redondea a céntimos por mes y por proyecto antes de sumar, para que los proyectos más
 * «sin asignar» den exactamente el total.
 */
@Service
@Transactional(readOnly = true)
public class FinanceService {

	static final int MAX_MONTHS = 36;

	private final MovementRepository movements;

	private final TimeEntryRepository timeEntries;

	private final ProjectRepository projects;

	private final CostService costs;

	private final Clock clock;

	public FinanceService(MovementRepository movements, TimeEntryRepository timeEntries, ProjectRepository projects,
			CostService costs, Clock clock) {
		this.movements = movements;
		this.timeEntries = timeEntries;
		this.projects = projects;
		this.costs = costs;
		this.clock = clock;
	}

	public FinanceSummaryResponse summary(AuthenticatedUser user, YearMonth from, YearMonth to) {
		if (!user.isAdmin()) {
			throw ApiErrors.forbidden();
		}
		YearMonth currentMonth = YearMonth.now(clock);
		if (from == null) {
			from = YearMonth.of(currentMonth.getYear(), 1);
		}
		if (to == null) {
			to = YearMonth.of(from.getYear(), 12);
		}
		if (to.isBefore(from)) {
			throw ApiErrors.badRequest("El mes final no puede ser anterior al inicial");
		}
		int monthCount = (int) from.until(to, ChronoUnit.MONTHS) + 1;
		if (monthCount > MAX_MONTHS) {
			throw ApiErrors.badRequest("El periodo no puede pasar de " + MAX_MONTHS + " meses");
		}

		MonthlyCosts monthlyCosts = costs.monthlyCosts(user.workspaceId());
		BigDecimal contractCostsPerMonth = Money.toCents(monthlyCosts.total());
		List<Movement> candidates = movements.findActiveBetween(user.workspaceId(), from.atDay(1), to.atEndOfMonth());
		Map<YearMonth, Map<UUID, BigDecimal>> timeCosts = timeCostsByMonthAndProject(user.workspaceId(), from, to);

		List<Month> months = new ArrayList<>();
		Map<UUID, Accumulator> byProject = new HashMap<>();
		Accumulator unassigned = new Accumulator();
		Accumulator total = new Accumulator();

		for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
			Accumulator monthTotals = new Accumulator();
			for (Movement movement : candidates) {
				if (movement.occurrenceIn(month).isPresent()) {
					monthTotals.add(movement);
					(movement.getProject() == null ? unassigned
							: byProject.computeIfAbsent(movement.getProject().getId(), id -> new Accumulator()))
							.add(movement);
				}
			}
			timeCosts.getOrDefault(month, Map.of()).forEach((projectId, cost) -> {
				monthTotals.timeCosts = monthTotals.timeCosts.add(cost);
				Accumulator project = byProject.computeIfAbsent(projectId, id -> new Accumulator());
				project.timeCosts = project.timeCosts.add(cost);
			});
			total.addAll(monthTotals);
			months.add(Month.of(month,
					Totals.of(monthTotals.income, monthTotals.expenses, contractCostsPerMonth, monthTotals.timeCosts),
					month.isAfter(currentMonth)));
		}

		BigDecimal periodMonths = BigDecimal.valueOf(monthCount);
		BigDecimal totalContractCosts = contractCostsPerMonth.multiply(periodMonths);
		BigDecimal assignedContractCosts = BigDecimal.ZERO;
		List<ProjectTotals> projectTotals = new ArrayList<>();
		for (Project project : projects.findByWorkspaceIdOrderByName(user.workspaceId())) {
			Accumulator acc = byProject.getOrDefault(project.getId(), new Accumulator());
			// Se redondea el coste mensual antes de multiplicar, igual que en el total, para que cuadre
			BigDecimal contractCosts = Money.toCents(monthlyCosts.forProject(project.getId())).multiply(periodMonths);
			assignedContractCosts = assignedContractCosts.add(contractCosts);
			projectTotals.add(ProjectTotals.of(project.getId(), project.getName(),
					Totals.of(acc.income, acc.expenses, contractCosts, acc.timeCosts)));
		}

		return new FinanceSummaryResponse(from, to, months,
				Totals.of(total.income, total.expenses, totalContractCosts, total.timeCosts),
				projectTotals,
				Totals.of(unassigned.income, unassigned.expenses, totalContractCosts.subtract(assignedContractCosts),
						BigDecimal.ZERO));
	}

	/** Coste del tiempo de cada proyecto en cada mes, ya redondeado a céntimos. */
	private Map<YearMonth, Map<UUID, BigDecimal>> timeCostsByMonthAndProject(UUID workspaceId, YearMonth from,
			YearMonth to) {
		Instant now = clock.instant();
		Instant start = from.atDay(1).atStartOfDay(clock.getZone()).toInstant();
		Instant end = to.plusMonths(1).atDay(1).atStartOfDay(clock.getZone()).toInstant();
		Map<YearMonth, Map<UUID, BigDecimal>> result = new HashMap<>();
		for (TimeEntry entry : timeEntries.findStartedBetween(workspaceId, start, end)) {
			YearMonth month = YearMonth.from(entry.getStartedAt().atZone(clock.getZone()));
			result.computeIfAbsent(month, m -> new HashMap<>())
					.merge(entry.getProject().getId(), entry.cost(now), BigDecimal::add);
		}
		result.values().forEach(byProject -> byProject.replaceAll((projectId, cost) -> Money.toCents(cost)));
		return result;
	}

	private static final class Accumulator {

		private BigDecimal income = BigDecimal.ZERO;

		private BigDecimal expenses = BigDecimal.ZERO;

		private BigDecimal timeCosts = BigDecimal.ZERO;

		void add(Movement movement) {
			if (movement.getType() == MovementType.INCOME) {
				income = income.add(movement.getAmount());
			} else {
				expenses = expenses.add(movement.getAmount());
			}
		}

		void addAll(Accumulator other) {
			income = income.add(other.income);
			expenses = expenses.add(other.expenses);
			timeCosts = timeCosts.add(other.timeCosts);
		}

	}

}
