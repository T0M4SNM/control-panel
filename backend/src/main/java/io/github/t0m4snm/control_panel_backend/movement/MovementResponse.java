package io.github.t0m4snm.control_panel_backend.movement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MovementResponse(
		UUID id,
		MovementType type,
		BigDecimal amount,
		LocalDate date,
		String description,
		Ref project,
		Ref contract,
		Recurrence recurrence,
		LocalDate endsOn) {

	static MovementResponse from(Movement m) {
		return new MovementResponse(m.getId(), m.getType(), m.getAmount(), m.getDate(), m.getDescription(),
				m.getProject() == null ? null : new Ref(m.getProject().getId(), m.getProject().getName()),
				m.getContract() == null ? null : new Ref(m.getContract().getId(), m.getContract().getName()),
				m.getRecurrence(), m.getEndsOn());
	}

	public record Ref(UUID id, String name) {
	}

}
