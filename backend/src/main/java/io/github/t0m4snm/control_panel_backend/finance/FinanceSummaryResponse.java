package io.github.t0m4snm.control_panel_backend.finance;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Resumen económico de un periodo. En cada fila, {@code profit = income - expenses - contractCosts}.
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
			BigDecimal profit, boolean projected) {
	}

	public record Totals(BigDecimal income, BigDecimal expenses, BigDecimal contractCosts, BigDecimal profit) {

		static Totals of(BigDecimal income, BigDecimal expenses, BigDecimal contractCosts) {
			return new Totals(income, expenses, contractCosts, income.subtract(expenses).subtract(contractCosts));
		}

	}

	public record ProjectTotals(UUID projectId, String projectName, BigDecimal income, BigDecimal expenses,
			BigDecimal contractCosts, BigDecimal profit) {
	}

}
