package io.github.t0m4snm.control_panel_backend.push;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.notification.Notification;
import io.github.t0m4snm.control_panel_backend.notification.NotificationRepository;
import io.github.t0m4snm.control_panel_backend.notification.Severity;

/**
 * Suscripciones y envío de Web Push. El aviso ya está guardado en {@code notification}; esto solo lo
 * entrega además al móvil u ordenador del usuario.
 */
@Service
@EnableConfigurationProperties(PushProperties.class)
public class PushService {

	/** Si el dispositivo está apagado, el servicio de push guarda el aviso hasta un día. */
	static final Duration TTL = Duration.ofDays(1);

	private static final int MAX_BODY_LENGTH = 1000;

	private static final Logger log = LoggerFactory.getLogger(PushService.class);

	private final PushSubscriptionRepository subscriptions;

	private final NotificationRepository notifications;

	private final PushProperties properties;

	private final Optional<VapidKeys> vapid;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private final Clock clock;

	public PushService(PushSubscriptionRepository subscriptions, NotificationRepository notifications,
			PushProperties properties, Clock clock) {
		this.subscriptions = subscriptions;
		this.notifications = notifications;
		this.properties = properties;
		this.clock = clock;
		this.vapid = loadKeys(properties);
	}

	/** Clave pública para {@code pushManager.subscribe}, o vacío si Web Push está desactivado. */
	public Optional<String> publicKey() {
		return vapid.map(VapidKeys::publicKey);
	}

	/** Da de alta el dispositivo, o lo actualiza si el endpoint ya existía. */
	@Transactional
	public void subscribe(AuthenticatedUser user, String endpoint, String p256dh, String auth, String userAgent) {
		if (vapid.isEmpty()) {
			throw ApiErrors.conflict("Web Push no está configurado en el servidor");
		}
		validateEndpoint(endpoint);
		validateKeys(p256dh, auth);
		subscriptions.findByEndpoint(endpoint).ifPresentOrElse(
				existing -> existing.update(user.id(), p256dh, auth, userAgent),
				() -> subscriptions.save(new PushSubscription(user.id(), endpoint, p256dh, auth, userAgent)));
	}

	/** Da de baja el dispositivo si es del usuario. No falla si ya no existía. */
	@Transactional
	public void unsubscribe(AuthenticatedUser user, String endpoint) {
		subscriptions.findByEndpoint(endpoint)
				.filter(s -> s.getUserId().equals(user.id()))
				.ifPresent(subscriptions::delete);
	}

	@Transactional(readOnly = true)
	public long subscriptionCount(AuthenticatedUser user) {
		return subscriptions.countByUserId(user.id());
	}

	/**
	 * Envía cada aviso a todos los dispositivos de su destinatario. Los fallos se registran pero no
	 * se propagan; las suscripciones que el servicio da por muertas (404 o 410) se borran.
	 */
	@Transactional
	public void deliver(List<UUID> notificationIds) {
		if (vapid.isEmpty()) {
			return;
		}
		List<Notification> toSend = notifications.findAllById(notificationIds);
		Map<UUID, List<PushSubscription>> byUser = subscriptions
				.findByUserIdIn(toSend.stream().map(Notification::getUserId).collect(Collectors.toSet())).stream()
				.collect(Collectors.groupingBy(PushSubscription::getUserId));
		for (Notification notification : toSend) {
			for (PushSubscription subscription : byUser.getOrDefault(notification.getUserId(), List.of())) {
				send(subscription, notification);
			}
		}
	}

	private void send(PushSubscription subscription, Notification notification) {
		try {
			byte[] body = WebPushCrypto.encrypt(payload(notification).getBytes(StandardCharsets.UTF_8),
					WebPushCrypto.fromBase64Url(subscription.getP256dh()),
					WebPushCrypto.fromBase64Url(subscription.getAuth()));
			URI endpoint = URI.create(subscription.getEndpoint());
			HttpRequest request = HttpRequest.newBuilder(endpoint)
					.timeout(Duration.ofSeconds(15))
					.header("Authorization", vapid.orElseThrow().authorizationHeader(endpoint, clock.instant()))
					.header("Content-Encoding", "aes128gcm")
					.header("Content-Type", "application/octet-stream")
					.header("TTL", String.valueOf(TTL.toSeconds()))
					.header("Urgency", notification.getSeverity() == Severity.CRITICAL ? "high" : "normal")
					.POST(HttpRequest.BodyPublishers.ofByteArray(body))
					.build();
			int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
			if (status == 404 || status == 410) {
				// El navegador anuló la suscripción: no tiene sentido seguir enviando
				subscriptions.delete(subscription);
			} else if (status >= 300) {
				log.warn("El servicio de push respondió {} para la suscripción {}", status, subscription.getId());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			log.warn("No se pudo enviar el aviso {} por Web Push: {}", notification.getId(), e.toString());
		}
	}

	/** Lo que recibe el service worker del frontend. */
	static String payload(Notification n) {
		String body = n.getMessage().length() > MAX_BODY_LENGTH ? n.getMessage().substring(0, MAX_BODY_LENGTH)
				: n.getMessage();
		return "{\"id\":\"%s\",\"type\":\"%s\",\"severity\":\"%s\",\"title\":%s,\"body\":%s,\"url\":\"/\"}"
				.formatted(n.getId(), n.getType(), n.getSeverity(), json(n.getTitle()), json(body));
	}

	private void validateEndpoint(String endpoint) {
		URI uri;
		try {
			uri = new URI(endpoint);
		} catch (URISyntaxException e) {
			throw ApiErrors.badRequest("El endpoint no es una URL válida");
		}
		boolean schemeOk = "https".equals(uri.getScheme()) || (properties.allowHttp() && "http".equals(uri.getScheme()));
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		boolean hostOk = properties.allowedEndpointHosts().stream()
				.map(h -> h.toLowerCase(Locale.ROOT))
				.anyMatch(h -> host.equals(h) || host.endsWith("." + h));
		if (!schemeOk || !hostOk) {
			throw ApiErrors.badRequest("El endpoint no es de un servicio de push conocido");
		}
	}

	private static void validateKeys(String p256dh, String auth) {
		try {
			WebPushCrypto.decodePublic(WebPushCrypto.fromBase64Url(p256dh));
			if (WebPushCrypto.fromBase64Url(auth).length != 16) {
				throw ApiErrors.badRequest("La clave auth debe tener 16 bytes");
			}
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw ApiErrors.badRequest("Las claves de la suscripción no son válidas");
		}
	}

	private static Optional<VapidKeys> loadKeys(PushProperties properties) {
		if (!properties.hasVapidKeys()) {
			log.info("Web Push desactivado: faltan APP_PUSH_VAPID_PUBLIC_KEY y APP_PUSH_VAPID_PRIVATE_KEY");
			return Optional.empty();
		}
		try {
			return Optional.of(VapidKeys.of(properties.vapidPublicKey(), properties.vapidPrivateKey(),
					properties.subject()));
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("Las claves VAPID no son válidas", e);
		}
	}

	/** Cadena JSON con comillas y escapes. */
	private static String json(String value) {
		StringBuilder out = new StringBuilder("\"");
		for (char c : value.toCharArray()) {
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20) {
						out.append("\\u%04x".formatted((int) c));
					} else {
						out.append(c);
					}
				}
			}
		}
		return out.append('"').toString();
	}

}
