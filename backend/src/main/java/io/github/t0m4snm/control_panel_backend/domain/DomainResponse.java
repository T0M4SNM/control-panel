package io.github.t0m4snm.control_panel_backend.domain;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.github.t0m4snm.control_panel_backend.contract.Contract;

/**
 * Dominio tal y como lo ve el usuario actual. {@code contract} solo se envía al {@code ADMIN} y es
 * el contrato efectivo: el propio o, en un subdominio, el del dominio raíz ({@code inherited}).
 */
public record DomainResponse(
		UUID id,
		String name,
		Ref parent,
		Ref server,
		Ref project,
		String registrar,
		String notes,
		@JsonInclude(JsonInclude.Include.NON_NULL) ContractInfo contract) {

	static DomainResponse forAdmin(Domain domain) {
		Contract contract = domain.effectiveContract();
		ContractInfo info = contract == null ? null
				: new ContractInfo(contract.getId(), contract.getName(), contract.getRenewalDate(),
						domain.getParent() != null);
		return from(domain, info);
	}

	static DomainResponse forMember(Domain domain) {
		return from(domain, null);
	}

	private static DomainResponse from(Domain d, ContractInfo contract) {
		return new DomainResponse(d.getId(), d.getName(),
				d.getParent() == null ? null : new Ref(d.getParent().getId(), d.getParent().getName()),
				d.getServer() == null ? null : new Ref(d.getServer().getId(), d.getServer().getName()),
				d.getProject() == null ? null : new Ref(d.getProject().getId(), d.getProject().getName()),
				d.getRegistrar(), d.getNotes(), contract);
	}

	public record Ref(UUID id, String name) {
	}

	/** @param renewalDate vencimiento del dominio; dispara los avisos de renovación */
	public record ContractInfo(UUID id, String name, LocalDate renewalDate, boolean inherited) {
	}

}
