package io.github.t0m4snm.control_panel_backend.log;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.ingest.IngestKey;
import io.github.t0m4snm.control_panel_backend.ingest.IngestKeyService;
import io.github.t0m4snm.control_panel_backend.ingest.IngestKeyService.CreatedIngestKey;
import io.github.t0m4snm.control_panel_backend.ingest.IngestKeyService.IngestKeyResponse;
import io.github.t0m4snm.control_panel_backend.log.LogService.Cursor;
import io.github.t0m4snm.control_panel_backend.log.LogService.Level;
import io.github.t0m4snm.control_panel_backend.log.LogService.LogLineResponse;
import io.github.t0m4snm.control_panel_backend.log.LogService.LogSourceResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
public class LogController {

	private static final int MAX_BODY = 5 * 1024 * 1024;

	private final LogService logs;

	private final IngestKeyService keys;

	public LogController(LogService logs, IngestKeyService keys) {
		this.logs = logs;
		this.keys = keys;
	}

	/** Punto de entrada de Vector: {@code Authorization: Bearer <clave del servidor>} y un lote de eventos. */
	@PostMapping("/api/ingest/logs")
	@ResponseStatus(HttpStatus.ACCEPTED)
	Map<String, Integer> ingest(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestBody String body) {
		if (body.length() > MAX_BODY) {
			throw ApiErrors.badRequest("Lote demasiado grande");
		}
		IngestKey key = keys.authenticate(authorization, 1);
		return Map.of("accepted", logs.ingest(key, body));
	}

	@GetMapping("/api/servers/{serverId}/logs")
	List<LogLineResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID serverId,
			@RequestParam(required = false) String source, @RequestParam(required = false) String level,
			@RequestParam(required = false) String q, @RequestParam(required = false) Instant beforeTime,
			@RequestParam(required = false) Long beforeId, @RequestParam(defaultValue = "200") int limit) {
		Level minLevel;
		try {
			minLevel = level == null || level.isBlank() ? null : Level.valueOf(level.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			throw ApiErrors.badRequest("level debe ser ERROR, WARN, INFO o DEBUG");
		}
		Cursor before = beforeTime != null && beforeId != null ? new Cursor(beforeTime, beforeId) : null;
		return logs.list(user, serverId, source, minLevel, q, before, limit);
	}

	@GetMapping("/api/servers/{serverId}/log-sources")
	List<LogSourceResponse> sources(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID serverId) {
		return logs.sources(user, serverId);
	}

	@GetMapping("/api/servers/{serverId}/ingest-keys")
	List<IngestKeyResponse> listKeys(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID serverId) {
		return keys.listForServer(user, serverId);
	}

	@PostMapping("/api/servers/{serverId}/ingest-keys")
	@ResponseStatus(HttpStatus.CREATED)
	CreatedIngestKey createKey(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID serverId,
			@Valid @RequestBody KeyRequest request) {
		return keys.createForServer(user, serverId, request.name());
	}

	record KeyRequest(@NotBlank @Size(max = 100) String name) {
	}

}
