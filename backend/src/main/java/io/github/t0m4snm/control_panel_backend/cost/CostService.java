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

		Map<ContractType, BigDecimal> byType = new EnumMap<>(ContractType.class);
		for (ContractType type : ContractType.values()) {
			byType.put(type, BigDecimal.ZERO);
		}
		BigDecimal total = BigDecimal.ZERO;
		for (Contract contract : contracts.findByWorkspaceIdAndStatus(user.workspaceId(), ContractStatus.ACTIVE)) {
			total = total.add(contract.monthlyAmount());
			byType.merge(contract.getType(), contract.monthlyAmount(), BigDecimal::add);
		}
		byType.replaceAll((type, amount) -> Money.toCents(amount));

		Map<UUID, BigDecimal> costByProject = new HashMap<>();
		for (ProjectServer link : links.findByWorkspaceId(user.workspaceId())) {
			Contract contract = link.getServer().getContract();
			if (contract != null && contract.isActive()) {
				costByProject.merge(link.getProject().getId(),
						Money.percentOf(contract.monthlyAmount(), link.getCostSharePercent()), BigDecimal::add);
			}
		}

		List<ProjectCost> projectCosts = projects.findByWorkspaceIdOrderByName(user.workspaceId()).stream()
				.map(p -> new ProjectCost(p.getId(), p.getName(),
						Money.toCents(costByProject.getOrDefault(p.getId(), BigDecimal.ZERO))))
				.toList();

		// Se resta sobre los importes ya redondeados para que las cifras que ve el usuario cuadren
		BigDecimal totalMonthly = Money.toCents(total);
		BigDecimal assigned = projectCosts.stream().map(ProjectCost::monthlyCost).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new CostSummaryResponse(totalMonthly, Money.toCents(total.multiply(MONTHS_PER_YEAR)), byType,
				projectCosts, totalMonthly.subtract(assigned));
	}

}
