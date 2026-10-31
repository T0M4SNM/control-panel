package io.github.t0m4snm.control_panel_backend.cost;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.common.Money;
import io.github.t0m4snm.control_panel_backend.contract.Contract;
import io.github.t0m4snm.control_panel_backend.contract.ContractRepository;
import io.github.t0m4snm.control_panel_backend.contract.ContractStatus;
import io.github.t0m4snm.control_panel_backend.contract.ContractType;
import io.github.t0m4snm.control_panel_backend.cost.CostSummaryResponse.ProjectCost;
import io.github.t0m4snm.control_panel_backend.project.ProjectRepository;
import io.github.t0m4snm.control_panel_backend.server.ProjectServer;
import io.github.t0m4snm.control_panel_backend.server.ProjectServerRepository;

/**
 * Calcula el coste recurrente a partir de los contratos activos; no se guarda en ningún sitio. Un
 * proyecto carga con el {@code cost_share_percent} del contrato de cada servidor que usa. Solo
 * para {@code ADMIN}.
 */
@Service
@Transactional(readOnly = true)
public class CostService {

	private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

	private final ContractRepository contracts;

	private final ProjectRepository projects;

	private final ProjectServerRepository links;

	public CostService(ContractRepository contracts, ProjectRepository projects, ProjectServerRepository links) {
		this.contracts = contracts;
		this.projects = projects;
		this.links = links;
	}

	public CostSummaryResponse summary(AuthenticatedUser user) {
		if (!user.isAdmin()) {
			throw ApiErrors.forbidden();
		}

		MonthlyCosts costs = monthlyCosts(user.workspaceId());
		Map<ContractType, BigDecimal> byType = new EnumMap<>(ContractType.class);
		costs.byType().forEach((type, amount) -> byType.put(type, Money.toCents(amount)));

		List<ProjectCost> projectCosts = projects.findByWorkspaceIdOrderByName(user.workspaceId()).stream()
				.map(p -> new ProjectCost(p.getId(), p.getName(), Money.toCents(costs.forProject(p.getId()))))
				.toList();

		// Se resta sobre los importes ya redondeados para que las cifras que ve el usuario cuadren
		BigDecimal totalMonthly = Money.toCents(costs.total());
		BigDecimal assigned = projectCosts.stream().map(ProjectCost::monthlyCost).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new CostSummaryResponse(totalMonthly, Money.toCents(costs.total().multiply(MONTHS_PER_YEAR)), byType,
				projectCosts, totalMonthly.subtract(assigned));
	}

	/** Coste mensual actual de los contratos activos, sin redondear, para otros cálculos. */
	public MonthlyCosts monthlyCosts(UUID workspaceId) {
		Map<ContractType, BigDecimal> byType = new EnumMap<>(ContractType.class);
		for (ContractType type : ContractType.values()) {
			byType.put(type, BigDecimal.ZERO);
		}
		BigDecimal total = BigDecimal.ZERO;
		for (Contract contract : contracts.findByWorkspaceIdAndStatus(workspaceId, ContractStatus.ACTIVE)) {
			total = total.add(contract.monthlyAmount());
			byType.merge(contract.getType(), contract.monthlyAmount(), BigDecimal::add);
		}

		Map<UUID, BigDecimal> byProject = new HashMap<>();
		for (ProjectServer link : links.findByWorkspaceId(workspaceId)) {
			Contract contract = link.getServer().getContract();
			if (contract != null && contract.isActive()) {
				byProject.merge(link.getProject().getId(),
						Money.percentOf(contract.monthlyAmount(), link.getCostSharePercent()), BigDecimal::add);
			}
		}
		return new MonthlyCosts(total, byType, byProject);
	}

	/**
	 * @param total coste mensual de todos los contratos activos
	 * @param byType total por tipo de contrato
	 * @param byProject coste mensual que carga cada proyecto según el reparto de sus servidores
	 */
	public record MonthlyCosts(BigDecimal total, Map<ContractType, BigDecimal> byType,
			Map<UUID, BigDecimal> byProject) {

		public BigDecimal forProject(UUID projectId) {
			return byProject.getOrDefault(projectId, BigDecimal.ZERO);
		}

	}

}
