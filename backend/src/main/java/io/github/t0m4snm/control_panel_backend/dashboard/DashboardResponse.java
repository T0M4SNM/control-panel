package io.github.t0m4snm.control_panel_backend.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.github.t0m4snm.control_panel_backend.contract.BillingPeriod;
import io.github.t0m4snm.control_panel_backend.contract.ContractType;
import io.github.t0m4snm.control_panel_backend.monitoring.CheckStatus;
import io.github.t0m4snm.control_panel_backend.project.ProjectStatus;
import io.github.t0m4snm.control_panel_backend.timetracking.TimeEntryResponse;

/**
 * Resumen para la pantalla de inicio. Las secciones con dinero ({@code renewals}, {@code costs} y
 * {@code thisMonth}) solo llegan al {@code ADMIN}: para los demás ni aparecen en el JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardResponse(
		Projects projects,
		Checks checks,
		List<SslExpiry> sslExpiring,
		long unreadNotifications,
		TimeEntryResponse runningTimer,
		List<Renewal> renewals,
		Costs costs,
		Month thisMonth) {

	/** Proyectos visibles por estado. */
	public record Projects(int total, Map<ProjectStatus, Long> byStatus) {
	}

	/** Comprobaciones activas por estado y las que están caídas ahora. */
	public record Checks(int total, Map<CheckStatus, Long> byStatus, List<DownCheck> down) {
	}

	public record DownCheck(UUID id, String name, String target, Instant since) {
	}

	public record SslExpiry(UUID checkId, String name, String target, Instant expiresAt, long daysLeft) {
	}

	/** Contrato activo que vence pronto; {@code daysLeft} negativo si ya pasó la fecha. */
	public record Renewal(UUID id, String name, String provider, ContractType type, LocalDate renewalDate,
			long daysLeft, BigDecimal amount, BillingPeriod billingPeriod, boolean autoRenew) {
	}

	public record Costs(BigDecimal totalMonthly, BigDecimal totalYearly) {
	}

	/** Mes en curso del resumen económico. */
	public record Month(BigDecimal income, BigDecimal expenses, BigDecimal contractCosts, BigDecimal timeCosts,
			BigDecimal profit, BigDecimal realProfit) {
	}

}
