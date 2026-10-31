package io.github.t0m4snm.control_panel_backend.apperror;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.common.Json;
import io.github.t0m4snm.control_panel_backend.ingest.IngestKey;
import io.github.t0m4snm.control_panel_backend.notification.NotificationDraft;
import io.github.t0m4snm.control_panel_backend.notification.NotificationRecipients;
import io.github.t0m4snm.control_panel_backend.notification.NotificationType;
import io.github.t0m4snm.control_panel_backend.notification.NotificationWriter;
import io.github.t0m4snm.control_panel_backend.notification.Severity;
import io.github.t0m4snm.control_panel_backend.project.Project;

/**
 * Recibe errores de una app (con la clave de su proyecto), los agrupa por huella y avisa al admin y
 * a los miembros del proyecto cuando aparece un error nuevo o vuelve uno que se había resuelto.
 */
@Service
public class ErrorIngestService {

	static final int MAX_BATCH = 50;

	static final int MAX_TYPE = 200;

	static final int MAX_MESSAGE = 4_000;

	static final int MAX_STACK = 32_000;

	static final int MAX_CONTEXT_FIELDS = 20;

	static final int MAX_CONTEXT_VALUE = 500;

	private final ErrorGroupRepository groups;

	private final ErrorEventRepository events;

	private final NotificationRecipients recipients;

	private final NotificationWriter notifications;

	private final Clock clock;

	public ErrorIngestService(ErrorGroupRepository groups, ErrorEventRepository events,
			NotificationRecipients recipients, NotificationWriter notifications, Clock clock) {
		this.groups = groups;
		this.events = events;
		this.recipients = recipients;
		this.notifications = notifications;
		this.clock = clock;
	}

	/**
	 * Un error tal y como lo manda una app.
	 *
	 * @param occurredAt cuándo ocurrió; si falta o viene del futuro, se usa la hora de llegada
	 * @param context datos sueltos de la petición (url, method, userId...), solo texto
	 */
	public record ErrorReport(String type, String message, String stack, Instant occurredAt, String environment,
			String release, Map<String, String> context) {
	}

	@Transactional
	public int ingest(IngestKey key, List<ErrorReport> reports) {
		Project project = key.getProject();
		if (project == null) {
			throw ApiErrors.badRequest("Esta clave es de un servidor; los errores se envían con una clave de proyecto");
		}
		if (reports.isEmpty() || reports.size() > MAX_BATCH) {
			throw ApiErrors.badRequest("Envía entre 1 y " + MAX_BATCH + " errores por petición");
		}
		Instant now = clock.instant();
		for (ErrorReport report : reports) {
			store(project, report, now);
		}
		return reports.size();
	}

	private void store(Project project, ErrorReport report, Instant now) {
		String type = truncate(blankTo(report.type(), "Error"), MAX_TYPE);
		String message = truncate(blankTo(report.message(), "(sin mensaje)"), MAX_MESSAGE);
		String stack = report.stack() == null || report.stack().isBlank() ? null : truncate(report.stack(), MAX_STACK);
		Instant at = report.occurredAt() == null || report.occurredAt().isAfter(now) ? now : report.occurredAt();
		String fingerprint = Fingerprint.of(type, message, stack);

		ErrorGroup group = groups.findByProjectIdAndFingerprint(project.getId(), fingerprint).orElse(null);
		boolean isNew = group == null;
		if (isNew) {
			group = groups.save(new ErrorGroup(project, fingerprint, type, message, at));
		}
		boolean reopened = group.occurred(message, at);
		events.save(new ErrorEvent(group, at, message, stack, truncate(report.environment(), 50),
				truncate(report.release(), 100), context(report.context())));
		// El aviso se escribe con SQL directo: el grupo tiene que estar ya en la base
		groups.flush();

		if (isNew || reopened) {
			String what = type + ": " + truncate(message, 300);
			notifications.notify(recipients.forProject(project.getWorkspace().getId(), project.getId()),
					new NotificationDraft(NotificationType.APP_ERROR, Severity.WARNING,
							(isNew ? "Nuevo error en " : "Vuelve un error en ") + project.getName(), what, null, null,
							isNew ? "error:%s:new".formatted(group.getId())
									: "error:%s:reopened:%d".formatted(group.getId(), at.getEpochSecond()),
							group.getId()));
		}
	}

	private static String context(Map<String, String> context) {
		if (context == null || context.isEmpty()) {
			return null;
		}
		Map<String, String> limited = new LinkedHashMap<>();
		context.entrySet().stream()
				.filter(e -> e.getKey() != null && e.getValue() != null)
				.limit(MAX_CONTEXT_FIELDS)
				.forEach(e -> limited.put(truncate(e.getKey(), 50), truncate(e.getValue(), MAX_CONTEXT_VALUE)));
		return Json.object(limited);
	}

	private static String blankTo(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

}
