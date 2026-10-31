package io.github.t0m4snm.control_panel_backend.project;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

	private final ProjectService service;

	public ProjectController(ProjectService service) {
		this.service = service;
	}

	@GetMapping
	List<ProjectResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
		return service.list(user);
	}

	@GetMapping("/{projectId}")
	ProjectResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
		return service.get(user, projectId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ProjectResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ProjectRequest request) {
		return service.create(user, request);
	}

	@PutMapping("/{projectId}")
	ProjectResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
			@Valid @RequestBody ProjectRequest request) {
		return service.update(user, projectId, request);
	}

	@DeleteMapping("/{projectId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
		service.delete(user, projectId);
	}

	@GetMapping("/{projectId}/members")
	List<MemberResponse> members(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
		return service.members(user, projectId);
	}

	@PutMapping("/{projectId}/members/{userId}")
	MemberResponse putMember(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
			@PathVariable UUID userId, @Valid @RequestBody MemberRequest request) {
		return service.putMember(user, projectId, userId, request.role());
	}

	@DeleteMapping("/{projectId}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeMember(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
			@PathVariable UUID userId) {
		service.removeMember(user, projectId, userId);
	}

	record MemberRequest(@NotNull ProjectRole role) {
	}

}
