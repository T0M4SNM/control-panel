package io.github.t0m4snm.control_panel_backend.finance;

import java.math.BigDecimal;
import java.time.Clock;
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

/**
 * Ingresos, gastos, coste de contratos y beneficio por mes y por proyecto. Solo {@code ADMIN}.
 * <p>
 * Los movimientos recurrentes cuentan en cada mes en que les toca. El coste de contratos de cada
 * mes es el coste mensual <em>actual</em>: los contratos no guardan desde cuándo están activos, así
 * que para meses pasados es una aproximación y para meses futuros una previsión.
 */
@Service
@Transactional(readOnly = true)
public class FinanceService {

	static final int MAX_MONTHS = 36;

	private final MovementRepository movements;

	private final ProjectRepository projects;

	private final CostService costs;

	private final Clock clock;

	public FinanceService(MovementRepository movements, ProjectRepository projects, CostService costs, Clock clock) {
		this.movements = movements;
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

		List<Month> months = new ArrayList<>();
		Map<UUID, Accumulator> byProject = new HashMap<>();
		Accumulator unassignedMovements = new Accumulator();
		BigDecimal totalIncome = BigDecimal.ZERO;
		BigDecimal totalExpenses = BigDecimal.ZERO;

		for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
			BigDecimal income = BigDecimal.ZERO;
			BigDecimal expenses = BigDecimal.ZERO;
			for (Movement movement : candidates) {
				if (movement.occurrenceIn(month).isEmpty()) {
					continue;
				}
				Accumulator target = movement.getProject() == null ? unassignedMovements
						: byProject.computeIfAbsent(movement.getProject().getId(), id -> new Accumulator());
				target.add(movement);
				if (movement.getType() == MovementType.INCOME) {
					income = income.add(movement.getAmount());
				} else {
					expenses = expenses.add(movement.getAmount());
				}
			}
			totalIncome = totalIncome.add(income);
			totalExpenses = totalExpenses.add(expenses);
			months.add(new Month(month, income, expenses, contractCostsPerMonth,
					income.subtract(expenses).subtract(contractCostsPerMonth), month.isAfter(currentMonth)));
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
			Totals totals = Totals.of(acc.income, acc.expenses, contractCosts);
			projectTotals.add(new ProjectTotals(project.getId(), project.getName(), totals.income(), totals.expenses(),
					totals.contractCosts(), totals.profit()));
		}

		return new FinanceSummaryResponse(from, to, months,
				Totals.of(totalIncome, totalExpenses, totalContractCosts),
				projectTotals,
				Totals.of(unassignedMovements.income, unassignedMovements.expenses,
						totalContractCosts.subtract(assignedContractCosts)));
	}

	private static final class Accumulator {

		private BigDecimal income = BigDecimal.ZERO;

		private BigDecimal expenses = BigDecimal.ZERO;

		void add(Movement movement) {
			if (movement.getType() == MovementType.INCOME) {
				income = income.add(movement.getAmount());
			} else {
				expenses = expenses.add(movement.getAmount());
			}
		}

	}

}
