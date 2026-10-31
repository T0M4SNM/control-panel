package io.github.t0m4snm.control_panel_backend.visit;

import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.visit.VisitService.Kind;
import io.github.t0m4snm.control_panel_backend.visit.VisitService.Range;
import io.github.t0m4snm.control_panel_backend.visit.VisitService.VisitBeacon;
import io.github.t0m4snm.control_panel_backend.visit.VisitService.VisitStats;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class VisitController {

	private static final int MAX_BODY = 2_000;

	private final VisitService visits;

	private final JsonMapper json;

	public VisitController(VisitService visits, JsonMapper json) {
		this.visits = visits;
		this.json = json;
	}

	/**
	 * Lo llama {@code visitas.js} desde la web del proyecto con {@code navigator.sendBeacon}, que envía el
	 * JSON como {@code text/plain}: así el navegador no hace la petición previa de CORS. Sin sesión ni
	 * clave; el id del proyecto es público y se comprueba el origen contra su URL pública.
	 */
	@PostMapping("/api/ingest/visits")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void ingest(@RequestBody String body, HttpServletRequest request) {
		if (body.length() > MAX_BODY) {
			throw ApiErrors.badRequest("Visita demasiado grande");
		}
		BeaconJson beacon;
		try {
			beacon = json.readValue(body, BeaconJson.class);
		}
		catch (JacksonException e) {
			throw ApiErrors.badRequest("JSON no válido");
		}
		if (beacon.project() == null) {
			throw ApiErrors.badRequest("Falta project");
		}
		Kind kind = "PING".equals(beacon.kind()) ? Kind.PING : Kind.VIEW;
		String origin = request.getHeader(HttpHeaders.ORIGIN);
		visits.record(new VisitBeacon(beacon.project(), kind, beacon.path(), beacon.referrer()), request.getRemoteAddr(),
				request.getHeader(HttpHeaders.USER_AGENT),
				origin != null && !"null".equals(origin) ? origin : request.getHeader(HttpHeaders.REFERER));
	}

	@GetMapping("/api/projects/{projectId}/visits")
	VisitStats stats(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
			@RequestParam(defaultValue = "DAY") String range) {
		try {
			return visits.stats(user, projectId, Range.valueOf(range.toUpperCase(Locale.ROOT)));
		}
		catch (IllegalArgumentException e) {
			throw ApiErrors.badRequest("range debe ser HOUR, DAY, WEEK, MONTH o QUARTER");
		}
	}

	record BeaconJson(UUID project, String kind, String path, String referrer) {
	}

}
