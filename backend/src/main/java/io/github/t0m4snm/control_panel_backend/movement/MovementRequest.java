package io.github.t0m4snm.control_panel_backend.movement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos de un movimiento para crear o reemplazar ({@code PUT}). {@code recurrence} vale
 * {@code ONE_TIME} si no llega; {@code endsOn} solo tiene sentido si se repite.
 */
public record MovementRequest(
		@NotNull MovementType type,
		@NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal amount,
		@NotNull LocalDate date,
		@NotBlank @Size(max = 500) String description,
		UUID projectId,
		UUID contractId,
		Recurrence recurrence,
		LocalDate endsOn) {

	Recurrence recurrenceOrDefault() {
		return recurrence == null ? Recurrence.ONE_TIME : recurrence;
	}

}
