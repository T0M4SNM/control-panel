package io.github.t0m4snm.control_panel_backend.visit;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.ingest.RateLimiter;
import io.github.t0m4snm.control_panel_backend.project.ProjectService;

/**
 * Visitas a las webs de los proyectos: las recibe del script {@code visitas.js} y calcula la gráfica de
 * usuarios activos. Sin cookies ni IP guardadas: el visitante es un hash con una sal que cambia cada día
 * y se borra (ver {@code V7__crear_visitas.sql}).
 */
@Service
@Transactional
public class VisitService {

	static final Duration RETENTION = Duration.ofDays(90);

	static final Duration ONLINE_WINDOW = Duration.ofMinutes(5);

	private static final int TOP = 10;

	/** Robots y herramientas que no son personas. */
	private static final Pattern BOT = Pattern.compile(
			"bot|crawl|spider|slurp|headless|lighthouse|preview|monitor|curl|wget|python|java/|go-http",
			Pattern.CASE_INSENSITIVE);

	private static final SecureRandom RANDOM = new SecureRandom();

	public enum Kind {
		VIEW, PING
	}

	/** Periodo de la gráfica y tamaño de cada punto. */
	public enum Range {
		HOUR(Duration.ofHours(1), Duration.ofMinutes(5)),
		DAY(Duration.ofDays(1), Duration.ofHours(1)),
		WEEK(Duration.ofDays(7), Duration.ofHours(1)),
		MONTH(Duration.ofDays(30), Duration.ofDays(1)),
		QUARTER(Duration.ofDays(90), Duration.ofDays(1));

		final Duration span;

		final Duration step;

		Range(Duration span, Duration step) {
			this.span = span;
			this.step = step;
		}

		boolean daily() {
			return step.equals(Duration.ofDays(1));
		}
	}

	private final JdbcTemplate jdbc;

	private final ProjectService projects;

	private final Clock clock;

	private final RateLimiter<String> perAddress;

	private volatile DaySalt currentSalt;

	public VisitService(JdbcTemplate jdbc, ProjectService projects, Clock clock,
			@Value("${app.visits.max-per-address-per-minute:120}") int maxPerAddress) {
		this.jdbc = jdbc;
		this.projects = projects;
		this.clock = clock;
		this.perAddress = new RateLimiter<>(maxPerAddress, clock);
	}

	/**
	 * Registra una visita. {@code origin} es la cabecera Origin (o Referer) del navegador: si el proyecto
	 * tiene URL pública, solo se aceptan visitas desde ese dominio o sus subdominios.
	 *
	 * @return false si se ignoró por venir de un robot
	 */
	public boolean record(VisitBeacon beacon, String address, String userAgent, String origin) {
		if (userAgent == null || userAgent.isBlank() || BOT.matcher(userAgent).find()) {
			return false;
		}
		if (!perAddress.tryAcquire(address, 1)) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas visitas desde esta dirección");
		}
		UUID projectId = beacon.projectId();
		List<String> found = jdbc.queryForList("SELECT public_url FROM project WHERE id = ?", String.class, projectId);
		if (found.isEmpty()) {
			throw ApiErrors.notFound();
		}
		String publicUrl = found.getFirst();
		String originHost = host(origin);
		if (publicUrl != null && !sameSite(originHost, host(publicUrl))) {
			throw ApiErrors.forbidden();
		}

		Instant now = clock.instant();
		String visitor = visitorHash(LocalDate.now(clock), projectId, address, userAgent);
		String referrer = beacon.kind() == Kind.VIEW ? clean(beacon.referrer(), 255) : null;
		if (referrer != null && referrer.equalsIgnoreCase(originHost)) {
			referrer = null;
		}
		jdbc.update("""
				INSERT INTO visit_event (project_id, visitor, kind, path, referrer_host, occurred_at)
				VALUES (?, ?, ?, ?, ?, ?)
				""", projectId, visitor, beacon.kind().name(),
				beacon.kind() == Kind.VIEW ? path(beacon.path()) : null, referrer, Timestamp.from(now));
		return true;
	}

	@Transactional(readOnly = true)
	public VisitStats stats(AuthenticatedUser user, UUID projectId, Range range) {
		projects.findVisible(user, projectId);
		Instant now = clock.instant();
		ZoneId zone = clock.getZone();

		// Puntos de la gráfica, con los huecos sin visitas a cero
		Map<Instant, Point> points = new LinkedHashMap<>();
		Instant from;
		if (range.daily()) {
			LocalDate today = LocalDate.now(clock);
			LocalDate first = today.minusDays(range.span.toDays() - 1);
			from = first.atStartOfDay(zone).toInstant();
			for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
				Instant start = d.atStartOfDay(zone).toInstant();
				points.put(start, new Point(start, 0, 0));
			}
			jdbc.query("""
					SELECT (occurred_at AT TIME ZONE ?)::date AS day, count(DISTINCT visitor), count(*) FILTER (WHERE kind = 'VIEW')
					FROM visit_event WHERE project_id = ? AND occurred_at >= ?
					GROUP BY day
					""", rs -> {
				Instant start = rs.getObject(1, LocalDate.class).atStartOfDay(zone).toInstant();
				points.computeIfPresent(start, (k, p) -> safePoint(k, rs));
			}, zone.getId(), projectId, Timestamp.from(from));
		}
		else {
			long step = range.step.toSeconds();
			Instant end = Instant.ofEpochSecond((now.getEpochSecond() / step + 1) * step);
			from = end.minus(range.span);
			for (Instant t = from; t.isBefore(end); t = t.plus(range.step)) {
				points.put(t, new Point(t, 0, 0));
			}
			jdbc.query("""
					SELECT date_bin(make_interval(secs => ?), occurred_at, TIMESTAMPTZ '2000-01-01 00:00:00+00') AS bucket,
					       count(DISTINCT visitor), count(*) FILTER (WHERE kind = 'VIEW')
					FROM visit_event WHERE project_id = ? AND occurred_at >= ?
					GROUP BY bucket
					""", rs -> {
				Instant start = rs.getTimestamp(1).toInstant();
				points.computeIfPresent(start, (k, p) -> safePoint(k, rs));
			}, step, projectId, Timestamp.from(from));
		}

		Map<String, Object> totals = jdbc.queryForMap("""
				SELECT count(DISTINCT (visitor, (occurred_at AT TIME ZONE ?)::date)) AS visitors,
				       count(*) FILTER (WHERE kind = 'VIEW') AS views
				FROM visit_event WHERE project_id = ? AND occurred_at >= ?
				""", zone.getId(), projectId, Timestamp.from(from));
		Long online = jdbc.queryForObject(
				"SELECT count(DISTINCT visitor) FROM visit_event WHERE project_id = ? AND occurred_at >= ?", Long.class,
				projectId, Timestamp.from(now.minus(ONLINE_WINDOW)));

		return new VisitStats(range, range.step.toSeconds(), new ArrayList<>(points.values()),
				((Number) totals.get("visitors")).longValue(), ((Number) totals.get("views")).longValue(),
				online == null ? 0 : online, top("path", projectId, from), top("referrer_host", projectId, from));
	}

	/** Borra las visitas de más de 90 días y las sales de días pasados (sin ellas, los hashes ya no se pueden rehacer). */
	public int purge() {
		jdbc.update("DELETE FROM visit_salt WHERE day < ?", LocalDate.now(clock));
		return jdbc.update("DELETE FROM visit_event WHERE occurred_at < ?", Timestamp.from(clock.instant().minus(RETENTION)));
	}

	private List<Count> top(String column, UUID projectId, Instant from) {
		// column es una constante de esta clase, nunca viene de fuera
		return jdbc.query("SELECT " + column + ", count(*) AS n FROM visit_event WHERE project_id = ? AND kind = 'VIEW' AND "
				+ column + " IS NOT NULL AND occurred_at >= ? GROUP BY 1 ORDER BY n DESC, 1 LIMIT " + TOP,
				(rs, i) -> new Count(rs.getString(1), rs.getLong(2)), projectId, Timestamp.from(from));
	}

	private String visitorHash(LocalDate day, UUID projectId, String address, String userAgent) {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			sha.update(salt(day));
			sha.update((projectId + "|" + address + "|" + userAgent).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(sha.digest());
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Sal del día: se crea con la primera visita y se guarda en la base de datos para sobrevivir a un reinicio. */
	private byte[] salt(LocalDate day) {
		DaySalt cached = currentSalt;
		if (cached != null && cached.day.equals(day)) {
			return cached.salt;
		}
		byte[] fresh = new byte[32];
		RANDOM.nextBytes(fresh);
		jdbc.update("INSERT INTO visit_salt (day, salt) VALUES (?, ?) ON CONFLICT (day) DO NOTHING", day, fresh);
		byte[] salt = jdbc.queryForObject("SELECT salt FROM visit_salt WHERE day = ?", byte[].class, day);
		currentSalt = new DaySalt(day, salt);
		return salt;
	}

	private static Point safePoint(Instant start, java.sql.ResultSet rs) {
		try {
			return new Point(start, rs.getLong(2), rs.getLong(3));
		}
		catch (java.sql.SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Solo la ruta: sin query ni fragmento, que pueden llevar datos personales o tokens. */
	static String path(String raw) {
		if (raw == null || raw.isBlank()) {
			return "/";
		}
		String path = raw.split("[?#]", 2)[0];
		return clean(path.startsWith("/") ? path : "/" + path, 500);
	}

	static String host(String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		try {
			String host = URI.create(url.contains("://") ? url : "https://" + url).getHost();
			return host == null ? null : host.toLowerCase();
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** El mismo dominio o un subdominio suyo; con o sin www. */
	static boolean sameSite(String host, String expected) {
		if (host == null || expected == null) {
			return false;
		}
		String base = expected.startsWith("www.") ? expected.substring(4) : expected;
		return host.equals(base) || host.endsWith("." + base);
	}

	private static String clean(String value, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}

	private record DaySalt(LocalDate day, byte[] salt) {
	}

	public record VisitBeacon(UUID projectId, Kind kind, String path, String referrer) {
	}

	public record Point(Instant start, long visitors, long views) {
	}

	public record Count(String name, long count) {
	}

	/**
	 * {@code visitors}: visitantes distintos en el periodo; como el hash cambia cada día, en periodos de
	 * varios días alguien que vuelve cuenta una vez por día. {@code online}: en los últimos 5 minutos.
	 */
	public record VisitStats(Range range, long stepSeconds, List<Point> points, long visitors, long views, long online,
			List<Count> topPages, List<Count> topReferrers) {
	}

}
