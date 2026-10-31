package io.github.t0m4snm.control_panel_backend.contract;

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

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

	private final ContractService service;

	public ContractController(ContractService service) {
		this.service = service;
	}

	@GetMapping
	List<ContractResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
		return service.list(user);
	}

	@GetMapping("/{contractId}")
	ContractResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID contractId) {
		return service.get(user, contractId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ContractResponse create(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody ContractRequest request) {
		return service.create(user, request);
	}

	@PutMapping("/{contractId}")
	ContractResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID contractId,
			@Valid @RequestBody ContractRequest request) {
		return service.update(user, contractId, request);
	}

	@DeleteMapping("/{contractId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID contractId) {
		service.delete(user, contractId);
	}

}
