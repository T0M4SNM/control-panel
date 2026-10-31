package io.github.t0m4snm.control_panel_backend.log;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.ingest.IngestKey;
import io.github.t0m4snm.control_panel_backend.ingest.RateLimiter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Logs de los servidores: los recibe de Vector con la clave del servidor y los enseña al admin. Solo el
 * admin: un servidor puede tener contenedores de varios proyectos y sus logs no deben cruzarse.
 */
@Service
@Transactional
public class LogService {

	static final int MAX_LINES_PER_REQUEST = 1_000;

	static final int MAX_MESSAGE = 8_000;

	static final int MAX_PAGE = 500;

	/** Primer nivel que aparece al principio del mensaje: «ERROR», «[warn]», «level=info»... */
	private static final Pattern LEVEL_IN_MESSAGE = Pattern.compile(
			"\\b(FATAL|CRIT(?:ICAL)?|ERR(?:OR)?|WARN(?:ING)?|INFO|DEBUG|TRACE)\\b", Pattern.CASE_INSENSITIVE);

	public enum Level {
		ERROR, WARN, INFO, DEBUG
	}

	private final JdbcTemplate jdbc;

	private final JsonMapper json;

	private final Clock clock;

	private final Duration retention;

	private final RateLimiter<UUID> perKey;

	public LogService(JdbcTemplate jdbc, JsonMapper json, Clock clock,
			@Value("${app.logs.retention-days:7}") int retentionDays,
			@Value("${app.logs.max-lines-per-minute:6000}") int maxLinesPerMinute) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
		this.retention = Duration.ofDays(retentionDays);
		this.perKey = new RateLimiter<>(maxLinesPerMinute, clock);
	}

	/**
	 * Guarda un lote de Vector: un array JSON (sink {@code http} con {@code codec: json}), un objeto o una
	 * línea JSON por evento. Entiende los campos de {@code docker_logs}, {@code file} y {@code journald}.
	 *
	 * @return líneas guardadas
	 */
	public int ingest(IngestKey key, String body) {
		if (key.getServer() == null) {
			throw ApiErrors.badRequest("Esta clave es de un proyecto; los logs se envían con la clave de un servidor");
		}
		List<JsonNode> events = parse(body);
		if (events.isEmpty() || events.size() > MAX_LINES_PER_REQUEST) {
			throw ApiErrors.badRequest("Envía entre 1 y " + MAX_LINES_PER_REQUEST + " líneas por petición");
		}
		if (!perKey.tryAcquire(key.getId(), events.size())) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas líneas; espera un minuto");
		}
		Instant now = clock.instant();
		UUID serverId = key.getServer().getId();
		List<Object[]> rows = new ArrayList<>(events.size());
		for (JsonNode event : events) {
			String message = message(event);
			if (message.isBlank()) {
				continue;
			}
			rows.add(new Object[] { serverId, source(event), level(event, message), truncate(message, MAX_MESSAGE),
					Timestamp.from(timestamp(event, now)) });
		}
		jdbc.batchUpdate("INSERT INTO log_line (server_id, source, level, message, occurred_at) VALUES (?, ?, ?, ?, ?)",
				rows);
		return rows.size();
	}

	/**
	 * Líneas de un servidor, de la más reciente a la más antigua. {@code minLevel}: ese nivel y los más
	 * graves. Para la página siguiente, {@code before} es el cursor de la última línea recibida.
	 */
	@Transactional(readOnly = true)
	public List<LogLineResponse> list(AuthenticatedUser user, UUID serverId, String source, Level minLevel, String text,
			Cursor before, int limit) {
		requireServer(user, serverId);
		StringBuilder sql = new StringBuilder(
				"SELECT id, source, level, message, occurred_at FROM log_line WHERE server_id = ?");
		List<Object> args = new ArrayList<>(List.of(serverId));
		if (source != null && !source.isBlank()) {
			sql.append(" AND source = ?");
			args.add(source);
		}
		if (minLevel != null) {
			List<String> levels = List.of(Level.values()).subList(0, minLevel.ordinal() + 1).stream().map(Level::name).toList();
			sql.append(" AND level IN (").append(String.join(", ", levels.stream().map(l -> "?").toList())).append(")");
			args.addAll(levels);
		}
		if (text != null && !text.isBlank()) {
			sql.append(" AND message ILIKE ? ESCAPE '\\'");
			args.add("%" + text.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
		}
		if (before != null) {
			sql.append(" AND (occurred_at, id) < (?, ?)");
			args.add(Timestamp.from(before.occurredAt()));
			args.add(before.id());
		}
		sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ?");
		args.add(Math.clamp(limit, 1, MAX_PAGE));
		return jdbc.query(sql.toString(), (rs, i) -> new LogLineResponse(rs.getLong(1), rs.getString(2),
				rs.getString(3) == null ? null : Level.valueOf(rs.getString(3)), rs.getString(4),
				rs.getTimestamp(5).toInstant()), args.toArray());
	}

	/** De dónde vienen los logs del servidor (contenedores, archivos...), con cuántas líneas y la última. */
	@Transactional(readOnly = true)
	public List<LogSourceResponse> sources(AuthenticatedUser user, UUID serverId) {
		requireServer(user, serverId);
		return jdbc.query("""
				SELECT source, count(*), count(*) FILTER (WHERE level = 'ERROR'), max(occurred_at)
				FROM log_line WHERE server_id = ? GROUP BY source ORDER BY max(occurred_at) DESC
				""", (rs, i) -> new LogSourceResponse(rs.getString(1), rs.getLong(2), rs.getLong(3),
				rs.getTimestamp(4).toInstant()), serverId);
	}

	public int purge() {
		return jdbc.update("DELETE FROM log_line WHERE occurred_at < ?", Timestamp.from(clock.instant().minus(retention)));
	}

	private void requireServer(AuthenticatedUser user, UUID serverId) {
		if (!user.isAdmin()) {
			throw ApiErrors.forbidden();
		}
		Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM server WHERE id = ? AND workspace_id = ?)",
				Boolean.class, serverId, user.workspaceId());
		if (!Boolean.TRUE.equals(exists)) {
			throw ApiErrors.notFound();
		}
	}

	private List<JsonNode> parse(String body) {
		List<JsonNode> events = new ArrayList<>();
		try {
			String trimmed = body.strip();
			if (trimmed.startsWith("[")) {
				json.readTree(trimmed).forEach(events::add);
			}
			else {
				for (String line : trimmed.split("\\R")) {
					if (!line.isBlank()) {
						events.add(json.readTree(line));
					}
				}
			}
		}
		catch (JacksonException e) {
			throw ApiErrors.badRequest("JSON no válido");
		}
		return events;
	}

	static String message(JsonNode event) {
		JsonNode message = event.get("message");
		if (message == null || message.isNull()) {
			return event.isObject() ? "" : event.asString();
		}
		return message.isString() ? message.asString().stripTrailing() : message.toString();
	}

	/** Contenedor, unidad de systemd o archivo; sin la barra inicial que pone Docker. */
	static String source(JsonNode event) {
		for (String field : List.of("source", "container_name", "_SYSTEMD_UNIT", "SYSLOG_IDENTIFIER", "appname", "file")) {
			String value = text(event, field);
			if (value != null) {
				if (field.equals("file")) {
					value = value.substring(value.lastIndexOf('/') + 1);
				}
				return truncate(value.startsWith("/") ? value.substring(1) : value, 200);
			}
		}
		return "desconocido";
	}

	/** El nivel que trae el evento; si no, el de journald (PRIORITY); si no, el que aparezca en el mensaje. */
	static String level(JsonNode event, String message) {
		String explicit = text(event, "level");
		if (explicit == null) {
			explicit = text(event, "severity");
		}
		if (explicit != null) {
			return normalize(explicit);
		}
		String priority = text(event, "PRIORITY");
		if (priority != null && priority.matches("[0-7]")) {
			int p = Integer.parseInt(priority);
			return p <= 3 ? "ERROR" : p == 4 ? "WARN" : p <= 6 ? "INFO" : "DEBUG";
		}
		Matcher matcher = LEVEL_IN_MESSAGE.matcher(message.length() > 200 ? message.substring(0, 200) : message);
		return matcher.find() ? normalize(matcher.group(1)) : null;
	}

	private static String normalize(String level) {
		String upper = level.toUpperCase(Locale.ROOT);
		if (upper.startsWith("FATAL") || upper.startsWith("CRIT") || upper.startsWith("ERR") || upper.startsWith("EMERG")
				|| upper.startsWith("ALERT")) {
			return "ERROR";
		}
		if (upper.startsWith("WARN")) {
			return "WARN";
		}
		if (upper.startsWith("DEBUG") || upper.startsWith("TRACE")) {
			return "DEBUG";
		}
		return upper.startsWith("INFO") || upper.startsWith("NOTICE") ? "INFO" : null;
	}

	/** La hora del evento; si falta, es rara o viene del futuro (reloj desajustado), la de llegada. */
	private static Instant timestamp(JsonNode event, Instant now) {
		String value = text(event, "timestamp");
		if (value != null) {
			try {
				Instant parsed = Instant.parse(value);
				if (parsed.isBefore(now.plus(Duration.ofMinutes(5)))) {
					return parsed;
				}
			}
			catch (DateTimeParseException e) {
				// Se usa la hora de llegada
			}
		}
		return now;
	}

	private static String text(JsonNode event, String field) {
		JsonNode node = event.get(field);
		if (node == null || node.isNull() || node.isContainer()) {
			return null;
		}
		String value = node.asString().strip();
		return value.isEmpty() ? null : value;
	}

	private static String truncate(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max);
	}

	/** Posición de una línea, para pedir las anteriores. */
	public record Cursor(Instant occurredAt, long id) {
	}

	public record LogLineResponse(long id, String source, Level level, String message, Instant occurredAt) {
	}

	public record LogSourceResponse(String name, long lines, long errors, Instant lastAt) {
	}

}
