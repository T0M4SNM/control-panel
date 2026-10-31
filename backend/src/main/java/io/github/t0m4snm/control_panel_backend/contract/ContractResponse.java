package io.github.t0m4snm.control_panel_backend.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import io.github.t0m4snm.control_panel_backend.common.Money;

/** {@code monthlyAmount} es el coste mensual equivalente (anual / 12), redondeado a céntimos. */
public record ContractResponse(
		UUID id,
		String name,
		String provider,
		ContractType type,
		BigDecimal amount,
		BillingPeriod billingPeriod,
		BigDecimal monthlyAmount,
		LocalDate renewalDate,
		boolean autoRenew,
		ContractStatus status,
		String notes) {

	static ContractResponse from(Contract c) {
		return new ContractResponse(c.getId(), c.getName(), c.getProvider(), c.getType(), c.getAmount(),
				c.getBillingPeriod(), Money.toCents(c.monthlyAmount()), c.getRenewalDate(), c.isAutoRenew(),
				c.getStatus(), c.getNotes());
	}

}
