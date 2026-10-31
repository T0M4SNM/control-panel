package io.github.t0m4snm.control_panel_backend.server;

import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

@RestController
public class ServerController {

	private final ServerService service;

	public ServerController(ServerService service) {
		this.service = service;
	}

	@GetMapping("/api/servers")
	List<ServerResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
		return service.list(user);
	}

	@GetMapping("/api/servers/{serverId}")
	ServerResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID serverId) {
		return service.get(user, serverId);
	}

	@PostMapping("/api/servers")
	@ResponseStatus(HttpStatus.CREATED)
	ServerResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ServerRequest request) {
		return service.create(user, request);
	}

	@PutMapping("/api/servers/{serverId}")
	ServerResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID serverId,
			@Valid @RequestBody ServerRequest request) {
		return service.update(user, serverId, request);
	}

	@DeleteMapping("/api/servers/{serverId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID serverId) {
		service.delete(user, serverId);
	}

	@GetMapping("/api/projects/{projectId}/servers")
	List<ServerResponse> listForProject(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID projectId) {
		return service.listForProject(user, projectId);
	}

	@PutMapping("/api/projects/{projectId}/servers/{serverId}")
	ServerResponse putProjectLink(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
			@PathVariable UUID serverId, @Valid @RequestBody ProjectLinkRequest request) {
		return service.putProjectLink(user, projectId, serverId, request.costSharePercent());
	}

	@DeleteMapping("/api/projects/{projectId}/servers/{serverId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeProjectLink(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
			@PathVariable UUID serverId) {
		service.removeProjectLink(user, projectId, serverId);
	}

	record ProjectLinkRequest(
			@NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2)
			BigDecimal costSharePercent) {
	}

}
