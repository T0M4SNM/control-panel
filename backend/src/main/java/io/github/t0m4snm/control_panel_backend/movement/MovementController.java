package io.github.t0m4snm.control_panel_backend.movement;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/movements")
public class MovementController {

	private final MovementService service;

	public MovementController(MovementService service) {
		this.service = service;
	}

	@GetMapping
	List<MovementResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) UUID projectId) {
		return service.list(user, projectId);
	}

	@GetMapping("/{movementId}")
	MovementResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID movementId) {
		return service.get(user, movementId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	MovementResponse create(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody MovementRequest request) {
		return service.create(user, request);
	}

	@PutMapping("/{movementId}")
	MovementResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID movementId,
			@Valid @RequestBody MovementRequest request) {
		return service.update(user, movementId, request);
	}

	@DeleteMapping("/{movementId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID movementId) {
		service.delete(user, movementId);
	}

}
