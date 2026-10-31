package io.github.t0m4snm.control_panel_backend.cost;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.t0m4snm.control_panel_backend.contract.ContractType;

/**
 * Coste recurrente del workspace, en euros al mes (los contratos anuales cuentan como anual / 12).
 *
 * @param totalMonthly todos los contratos activos
 * @param totalYearly {@code totalMonthly * 12}
 * @param byType total mensual por tipo de contrato
 * @param projects coste mensual de cada proyecto, según el reparto de sus servidores
 * @param unassignedMonthly parte del total que no carga ningún proyecto: contratos sin servidor,
 * servidores sin proyecto o repartos que no llegan al 100 %
 */
public record CostSummaryResponse(
		BigDecimal totalMonthly,
		BigDecimal totalYearly,
		Map<ContractType, BigDecimal> byType,
		List<ProjectCost> projects,
		BigDecimal unassignedMonthly) {

	public record ProjectCost(UUID projectId, String projectName, BigDecimal monthlyCost) {
	}

}
