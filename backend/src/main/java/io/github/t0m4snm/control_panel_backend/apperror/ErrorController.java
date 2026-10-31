package io.github.t0m4snm.control_panel_backend.apperror;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.t0m4snm.control_panel_backend.apperror.ErrorIngestService.ErrorReport;
import io.github.t0m4snm.control_panel_backend.apperror.ErrorService.ErrorDetailResponse;
import io.github.t0m4snm.control_panel_backend.apperror.ErrorService.ErrorGroupResponse;
import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.ingest.IngestKey;
import io.github.t0m4snm.control_panel_backend.ingest.IngestKeyService;
import io.github.t0m4snm.control_panel_backend.ingest.IngestKeyService.CreatedIngestKey;
import io.github.t0m4snm.control_panel_backend.ingest.IngestKeyService.IngestKeyResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
public class ErrorController {

	private final ErrorIngestService ingest;

	private final ErrorService errors;

	private final IngestKeyService keys;

	public ErrorController(ErrorIngestService ingest, ErrorService errors, IngestKeyService keys) {
		this.ingest = ingest;
		this.errors = errors;
		this.keys = keys;
	}

	/**
	 * Punto de entrada de las apps: {@code Authorization: Bearer <clave del proyecto>} y
	 * {@code { "errors": [ ... ] }}. Sin sesión ni CSRF: se autentica con la clave.
	 */
	@PostMapping("/api/ingest/errors")
	@ResponseStatus(HttpStatus.ACCEPTED)
	Map<String, Integer> ingest(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestBody ErrorBatch batch) {
		List<ErrorReport> reports = batch.errors() == null ? List.of() : batch.errors();
		IngestKey key = keys.authenticate(authorization, Math.max(1, reports.size()));
		return Map.of("accepted", ingest.ingest(key, reports));
	}

	@GetMapping("/api/projects/{projectId}/errors")
	List<ErrorGroupResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
			@RequestParam(defaultValue = "OPEN") String status) {
		if ("ALL".equals(status)) {
			return errors.list(user, projectId, null);
		}
		try {
			return errors.list(user, projectId, ErrorGroup.Status.valueOf(status));
		} catch (IllegalArgumentException e) {
			throw ApiErrors.badRequest("status debe ser OPEN, RESOLVED o ALL");
		}
	}

	@GetMapping("/api/errors/{groupId}")
	ErrorDetailResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID groupId) {
		return errors.get(user, groupId);
	}

	@PostMapping("/api/errors/{groupId}/resolve")
	ErrorGroupResponse resolve(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID groupId) {
		return errors.resolve(user, groupId);
	}

	@PostMapping("/api/errors/{groupId}/reopen")
	ErrorGroupResponse reopen(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID groupId) {
		return errors.reopen(user, groupId);
	}

	@GetMapping("/api/projects/{projectId}/ingest-keys")
	List<IngestKeyResponse> listKeys(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
		return keys.listForProject(user, projectId);
	}

	@PostMapping("/api/projects/{projectId}/ingest-keys")
	@ResponseStatus(HttpStatus.CREATED)
	CreatedIngestKey createKey(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
			@Valid @RequestBody KeyRequest request) {
		return keys.createForProject(user, projectId, request.name());
	}

	@DeleteMapping("/api/ingest-keys/{keyId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revokeKey(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID keyId) {
		keys.revoke(user, keyId);
	}

	record ErrorBatch(List<ErrorReport> errors) {
	}

	record KeyRequest(@NotBlank @Size(max = 100) String name) {
	}

}
