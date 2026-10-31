package io.github.t0m4snm.control_panel_backend.finance;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Resumen económico de un periodo. En cada fila:
 * <ul>
 * <li>{@code profit = income - expenses - contractCosts}: beneficio en dinero.</li>
 * <li>{@code realProfit = profit - timeCosts}: descontando también el tiempo dedicado, valorado con
 * la tarifa de cada entrada.</li>
 * </ul>
 *
 * @param months una fila por mes; {@code projected} marca los meses que aún no han llegado
 * @param projects beneficio de cada proyecto en el periodo
 * @param unassigned lo que no es de ningún proyecto: movimientos sin proyecto y coste de contratos
 * sin asignar
 */
public record FinanceSummaryResponse(
		YearMonth from,
		YearMonth to,
		List<Month> months,
		Totals totals,
		List<ProjectTotals> projects,
		Totals unassigned) {

	public record Month(YearMonth month, BigDecimal income, BigDecimal expenses, BigDecimal contractCosts,
			BigDecimal timeCosts, BigDecimal profit, BigDecimal realProfit, boolean projected) {

		static Month of(YearMonth month, Totals totals, boolean projected) {
			return new Month(month, totals.income(), totals.expenses(), totals.contractCosts(), totals.timeCosts(),
					totals.profit(), totals.realProfit(), projected);
		}

	}

	public record Totals(BigDecimal income, BigDecimal expenses, BigDecimal contractCosts, BigDecimal timeCosts,
			BigDecimal profit, BigDecimal realProfit) {

		static Totals of(BigDecimal income, BigDecimal expenses, BigDecimal contractCosts, BigDecimal timeCosts) {
			BigDecimal profit = income.subtract(expenses).subtract(contractCosts);
			return new Totals(income, expenses, contractCosts, timeCosts, profit, profit.subtract(timeCosts));
		}

	}

	public record ProjectTotals(UUID projectId, String projectName, BigDecimal income, BigDecimal expenses,
			BigDecimal contractCosts, BigDecimal timeCosts, BigDecimal profit, BigDecimal realProfit) {

		static ProjectTotals of(UUID projectId, String projectName, Totals totals) {
			return new ProjectTotals(projectId, projectName, totals.income(), totals.expenses(),
					totals.contractCosts(), totals.timeCosts(), totals.profit(), totals.realProfit());
		}

	}

}
