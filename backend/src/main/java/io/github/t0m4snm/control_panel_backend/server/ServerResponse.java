package io.github.t0m4snm.control_panel_backend.server;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.github.t0m4snm.control_panel_backend.common.Money;
import io.github.t0m4snm.control_panel_backend.contract.Contract;

/**
 * Servidor tal y como lo ve el usuario actual. Los datos económicos ({@code costs} y el reparto de
 * cada proyecto) solo se envían al {@code ADMIN}: para los demás ni siquiera aparecen en el JSON.
 * Un miembro solo ve los proyectos del servidor en los que participa.
 */
public record ServerResponse(
		UUID id,
		String name,
		String provider,
		String ipAddress,
		String os,
		Integer cpuCores,
		Integer ramMb,
		Integer diskGb,
		String notes,
		@JsonInclude(JsonInclude.Include.NON_NULL) Costs costs,
		List<ProjectLink> projects) {

	static ServerResponse forAdmin(Server server, List<ProjectServer> links) {
		Contract contract = server.getContract();
		BigDecimal monthly = contract != null && contract.isActive() ? contract.monthlyAmount() : BigDecimal.ZERO;
		BigDecimal assigned = links.stream()
				.map(ProjectServer::getCostSharePercent)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		Costs costs = new Costs(contract == null ? null : contract.getId(),
				contract == null ? null : contract.getName(), Money.toCents(monthly), assigned);
		List<ProjectLink> projects = sorted(links).stream()
				.map(link -> new ProjectLink(link.getProject().getId(), link.getProject().getName(),
						link.getCostSharePercent(), Money.toCents(Money.percentOf(monthly, link.getCostSharePercent()))))
				.toList();
		return from(server, costs, projects);
	}

	static ServerResponse forMember(Server server, List<ProjectServer> visibleLinks) {
		List<ProjectLink> projects = sorted(visibleLinks).stream()
				.map(link -> new ProjectLink(link.getProject().getId(), link.getProject().getName(), null, null))
				.toList();
		return from(server, null, projects);
	}

	private static ServerResponse from(Server s, Costs costs, List<ProjectLink> projects) {
		return new ServerResponse(s.getId(), s.getName(), s.getProvider(), s.getIpAddress(), s.getOs(),
				s.getCpuCores(), s.getRamMb(), s.getDiskGb(), s.getNotes(), costs, projects);
	}

	private static List<ProjectServer> sorted(List<ProjectServer> links) {
		return links.stream().sorted(Comparator.comparing(link -> link.getProject().getName())).toList();
	}

	/**
	 * @param monthlyAmount coste mensual del contrato si está activo; 0 si no hay contrato o está cancelado
	 * @param assignedSharePercent suma de los repartos; lo que falta hasta 100 es coste sin asignar
	 */
	public record Costs(UUID contractId, String contractName, BigDecimal monthlyAmount,
			BigDecimal assignedSharePercent) {
	}

	public record ProjectLink(
			UUID projectId,
			String projectName,
			@JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal costSharePercent,
			@JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal monthlyCost) {
	}

}
