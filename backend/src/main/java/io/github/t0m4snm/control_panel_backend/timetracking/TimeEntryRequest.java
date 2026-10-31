package io.github.t0m4snm.control_panel_backend.timetracking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Entrada de tiempo manual, o edición de una existente. {@code hourlyRate} solo lo puede cambiar el
 * {@code ADMIN}; si no llega, se usa la tarifa actual del usuario (al crear) o se mantiene (al editar).
 */
public record TimeEntryRequest(
		@NotNull UUID projectId,
		@NotNull Instant startedAt,
		Instant endedAt,
		@Size(max = 500) String description,
		@DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal hourlyRate) {
}
