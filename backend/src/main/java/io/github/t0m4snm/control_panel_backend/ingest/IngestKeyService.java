package io.github.t0m4snm.control_panel_backend.ingest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.project.Project;
import io.github.t0m4snm.control_panel_backend.project.ProjectService;

/**
 * Claves de envío: el admin las crea y revoca; las apps las usan en la cabecera
 * {@code Authorization: Bearer <clave>} para enviar datos a {@code /api/ingest/...}.
 */
@Service
@Transactional
public class IngestKeyService {

	/** Prefijo reconocible, como hacen GitHub o Stripe: ayuda a detectar una clave filtrada. */
	static final String PREFIX = "cpk_";

	private static final SecureRandom RANDOM = new SecureRandom();

	private final IngestKeyRepository keys;

	private final ProjectService projects;

	private final Clock clock;

	private final RateLimiter<UUID> rateLimiter;

	public IngestKeyService(IngestKeyRepository keys, ProjectService projects, Clock clock,
			@Value("${app.ingest.max-events-per-minute:600}") int maxEventsPerMinute) {
		this.keys = keys;
		this.projects = projects;
		this.clock = clock;
		this.rateLimiter = new RateLimiter<>(maxEventsPerMinute, clock);
	}

	@Transactional(readOnly = true)
	public List<IngestKeyResponse> listForProject(AuthenticatedUser user, UUID projectId) {
		requireAdmin(user);
		projects.findVisible(user, projectId);
		return keys.findByProjectIdOrderByCreatedAtDesc(projectId).stream().map(IngestKeyResponse::from).toList();
	}

	/** Crea una clave para un proyecto. Devuelve la clave en claro: es la única vez que se puede ver. */
	public CreatedIngestKey createForProject(AuthenticatedUser user, UUID projectId, String name) {
		requireAdmin(user);
		Project project = projects.findVisible(user, projectId);
		String plain = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
		IngestKey key = keys.save(new IngestKey(project.getWorkspace(), project, null, name.trim(), hash(plain),
				plain.substring(0, 10)));
		return new CreatedIngestKey(IngestKeyResponse.from(key), plain);
	}

	public void revoke(AuthenticatedUser user, UUID keyId) {
		requireAdmin(user);
		keys.findByIdAndWorkspaceId(keyId, user.workspaceId()).orElseThrow(ApiErrors::notFound).revoke(clock.instant());
	}

	/**
	 * Valida la cabecera {@code Authorization} de un envío y cuenta {@code events} contra el límite por
	 * minuto. 401 si la clave no existe o está revocada; 429 si se pasa del límite.
	 */
	@Transactional
	public IngestKey authenticate(String authorization, int events) {
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Falta la cabecera Authorization: Bearer <clave>");
		}
		IngestKey key = keys.findByKeyHash(hash(authorization.substring("Bearer ".length()).trim()))
				.filter(k -> !k.isRevoked())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clave no válida o revocada"));
		if (!rateLimiter.tryAcquire(key.getId(), events)) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados envíos; espera un minuto");
		}
		key.touch(Instant.now(clock));
		return key;
	}

	static String hash(String plain) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(plain.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static byte[] randomBytes(int length) {
		byte[] bytes = new byte[length];
		RANDOM.nextBytes(bytes);
		return bytes;
	}

	private static void requireAdmin(AuthenticatedUser user) {
		if (!user.isAdmin()) {
			throw ApiErrors.forbidden();
		}
	}

	public record IngestKeyResponse(UUID id, String name, String keyPrefix, Instant createdAt, Instant lastUsedAt,
			Instant revokedAt) {

		static IngestKeyResponse from(IngestKey k) {
			return new IngestKeyResponse(k.getId(), k.getName(), k.getKeyPrefix(), k.getCreatedAt(), k.getLastUsedAt(),
					k.getRevokedAt());
		}

	}

	/** @param key la clave en claro; no se vuelve a poder consultar */
	public record CreatedIngestKey(IngestKeyResponse info, String key) {
	}

}
