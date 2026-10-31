package io.github.t0m4snm.control_panel_backend.domain;

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

@RestController
public class DomainController {

	private final DomainService service;

	public DomainController(DomainService service) {
		this.service = service;
	}

	@GetMapping("/api/domains")
	List<DomainResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
		return service.list(user);
	}

	@GetMapping("/api/domains/{domainId}")
	DomainResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID domainId) {
		return service.get(user, domainId);
	}

	@PostMapping("/api/domains")
	@ResponseStatus(HttpStatus.CREATED)
	DomainResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody DomainRequest request) {
		return service.create(user, request);
	}

	@PutMapping("/api/domains/{domainId}")
	DomainResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID domainId,
			@Valid @RequestBody DomainRequest request) {
		return service.update(user, domainId, request);
	}

	@DeleteMapping("/api/domains/{domainId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID domainId) {
		service.delete(user, domainId);
	}

	@GetMapping("/api/projects/{projectId}/domains")
	List<DomainResponse> listForProject(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID projectId) {
		return service.listForProject(user, projectId);
	}

}
