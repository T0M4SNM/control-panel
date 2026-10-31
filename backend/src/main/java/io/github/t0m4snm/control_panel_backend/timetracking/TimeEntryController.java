package io.github.t0m4snm.control_panel_backend.timetracking;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
public class TimeEntryController {

	private final TimeEntryService service;

	public TimeEntryController(TimeEntryService service) {
		this.service = service;
	}

	/** Cronómetro en marcha del usuario actual; 204 si no hay ninguno. */
	@GetMapping("/api/timer")
	ResponseEntity<TimeEntryResponse> current(@AuthenticationPrincipal AuthenticatedUser user) {
		return service.current(user)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PostMapping("/api/timer/start")
	@ResponseStatus(HttpStatus.CREATED)
	TimeEntryResponse start(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody StartRequest request) {
		return service.start(user, request.projectId(), request.description());
	}

	@PostMapping("/api/timer/stop")
	TimeEntryResponse stop(@AuthenticationPrincipal AuthenticatedUser user) {
		return service.stop(user);
	}

	@GetMapping("/api/time-entries")
	List<TimeEntryResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID userId) {
		return service.list(user, from, to, projectId, userId);
	}

	@PostMapping("/api/time-entries")
	@ResponseStatus(HttpStatus.CREATED)
	TimeEntryResponse create(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody TimeEntryRequest request) {
		return service.create(user, request);
	}

	@PutMapping("/api/time-entries/{entryId}")
	TimeEntryResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID entryId,
			@Valid @RequestBody TimeEntryRequest request) {
		return service.update(user, entryId, request);
	}

	@DeleteMapping("/api/time-entries/{entryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID entryId) {
		service.delete(user, entryId);
	}

	record StartRequest(@NotNull UUID projectId, @Size(max = 500) String description) {
	}

}
