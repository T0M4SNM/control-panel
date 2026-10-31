package io.github.t0m4snm.control_panel_backend.monitoring;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.monitoring.HealthCheckResponse.Result;
import jakarta.validation.Valid;

@RestController
public class HealthCheckController {

	private final HealthCheckService service;

	public HealthCheckController(HealthCheckService service) {
		this.service = service;
	}

	@GetMapping("/api/health-checks")
	List<HealthCheckResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
		return service.list(user);
	}

	@GetMapping("/api/health-checks/{checkId}")
	HealthCheckResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID checkId) {
		return service.get(user, checkId);
	}

	/** Últimos resultados, del más reciente al más antiguo (50 por defecto, máximo 500). */
	@GetMapping("/api/health-checks/{checkId}/results")
	List<Result> results(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID checkId,
			@RequestParam(required = false) Integer limit) {
		return service.results(user, checkId, limit);
	}

	@PostMapping("/api/health-checks")
	@ResponseStatus(HttpStatus.CREATED)
	HealthCheckResponse create(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody HealthCheckRequest request) {
		return service.create(user, request);
	}

	@PutMapping("/api/health-checks/{checkId}")
	HealthCheckResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID checkId,
			@Valid @RequestBody HealthCheckRequest request) {
		return service.update(user, checkId, request);
	}

	@DeleteMapping("/api/health-checks/{checkId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID checkId) {
		service.delete(user, checkId);
	}

	@GetMapping("/api/projects/{projectId}/health-checks")
	List<HealthCheckResponse> listForProject(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID projectId) {
		return service.listForProject(user, projectId);
	}

}
