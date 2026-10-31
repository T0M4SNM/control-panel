package io.github.t0m4snm.control_panel_backend.contract;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos de un contrato para crear o reemplazar ({@code PUT}). {@code autoRenew} vale {@code true} y
 * {@code status} vale {@code ACTIVE} si no llegan.
 */
public record ContractRequest(
		@NotBlank @Size(max = 150) String name,
		@NotBlank @Size(max = 100) String provider,
		@NotNull ContractType type,
		@NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amount,
		@NotNull BillingPeriod billingPeriod,
		@NotNull LocalDate renewalDate,
		Boolean autoRenew,
		ContractStatus status,
		String notes) {

	void applyTo(Contract contract) {
		contract.setName(name.trim());
		contract.setProvider(provider.trim());
		contract.setType(type);
		contract.setAmount(amount);
		contract.setBillingPeriod(billingPeriod);
		contract.setRenewalDate(renewalDate);
		contract.setAutoRenew(autoRenew == null || autoRenew);
		contract.setStatus(status == null ? ContractStatus.ACTIVE : status);
		contract.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
	}

}
