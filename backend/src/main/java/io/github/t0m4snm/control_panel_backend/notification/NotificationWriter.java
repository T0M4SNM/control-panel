package io.github.t0m4snm.control_panel_backend.notification;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea avisos sin repetirlos. Usa {@code INSERT ... ON CONFLICT DO NOTHING} sobre el índice único
 * {@code (user_id, dedupe_key)}: aunque la tarea se ejecute dos veces, o dos procesos a la vez, la
 * base solo deja pasar uno. Publica {@link NotificationsCreated} con los que se han creado de verdad.
 */
@Component
public class NotificationWriter {

	private static final String INSERT = """
			INSERT INTO notification (user_id, type, severity, title, message, contract_id, health_check_id,
			                          dedupe_key, created_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (user_id, dedupe_key) DO NOTHING
			RETURNING id
			""";

	private static final int MAX_TITLE_LENGTH = 200;

	private final JdbcTemplate jdbc;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public NotificationWriter(JdbcTemplate jdbc, ApplicationEventPublisher events, Clock clock) {
		this.jdbc = jdbc;
		this.events = events;
		this.clock = clock;
	}

	/** Crea el aviso para cada destinatario que aún no lo tenga; devuelve los ids creados. */
	@Transactional
	public List<UUID> notify(Collection<UUID> recipientIds, NotificationDraft draft) {
		Timestamp now = Timestamp.from(clock.instant());
		String title = draft.title().length() > MAX_TITLE_LENGTH ? draft.title().substring(0, MAX_TITLE_LENGTH)
				: draft.title();
		List<UUID> created = new ArrayList<>();
		for (UUID userId : recipientIds) {
			// Con ON CONFLICT DO NOTHING, RETURNING no devuelve fila si ya existía
			created.addAll(jdbc.queryForList(INSERT, UUID.class, userId, draft.type().name(), draft.severity().name(),
					title, draft.message(), draft.contractId(), draft.healthCheckId(), draft.dedupeKey(), now));
		}
		if (!created.isEmpty()) {
			events.publishEvent(new NotificationsCreated(created));
		}
		return created;
	}

	/** Avisos recién creados; el envío por Web Push escucha este evento. */
	public record NotificationsCreated(List<UUID> notificationIds) {
	}

}
