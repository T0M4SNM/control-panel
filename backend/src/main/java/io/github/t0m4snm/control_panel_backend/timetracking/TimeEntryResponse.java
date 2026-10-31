package io.github.t0m4snm.control_panel_backend.timetracking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.github.t0m4snm.control_panel_backend.common.Money;

/**
 * Entrada de tiempo. {@code durationSeconds} se calcula; si sigue en marcha, hasta ahora. La tarifa
 * y el coste son dinero: solo llegan al {@code ADMIN}.
 */
public record TimeEntryResponse(
		UUID id,
		Ref user,
		Ref project,
		Instant startedAt,
		Instant endedAt,
		boolean running,
		long durationSeconds,
		String description,
		@JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal hourlyRate,
		@JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal cost) {

	static TimeEntryResponse from(TimeEntry t, Instant now, boolean includeMoney) {
		return new TimeEntryResponse(t.getId(), new Ref(t.getUser().getId(), t.getUser().getDisplayName()),
				new Ref(t.getProject().getId(), t.getProject().getName()), t.getStartedAt(), t.getEndedAt(),
				t.isRunning(), t.duration(now).toSeconds(), t.getDescription(),
				includeMoney ? t.getHourlyRate() : null,
				includeMoney ? Money.toCents(t.cost(now)) : null);
	}

	public record Ref(UUID id, String name) {
	}

}
