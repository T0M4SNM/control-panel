package io.github.t0m4snm.control_panel_backend.push;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un navegador o dispositivo suscrito a Web Push. Un usuario puede tener varias (móvil, ordenador...);
 * el {@code endpoint} es único y lo da el servicio de push del navegador.
 */
@Entity
@Table(name = "push_subscription")
public class PushSubscription {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(nullable = false, unique = true, columnDefinition = "text")
	private String endpoint;

	@Column(nullable = false)
	private String p256dh;

	@Column(nullable = false)
	private String auth;

	@Column(name = "user_agent", length = 500)
	private String userAgent;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	protected PushSubscription() {
	}

	public PushSubscription(UUID userId, String endpoint, String p256dh, String auth, String userAgent) {
		this.endpoint = endpoint;
		update(userId, p256dh, auth, userAgent);
	}

	/** El navegador puede renovar las claves, o el dispositivo cambiar de usuario. */
	public void update(UUID userId, String p256dh, String auth, String userAgent) {
		this.userId = userId;
		this.p256dh = p256dh;
		this.auth = auth;
		this.userAgent = userAgent == null || userAgent.length() <= 500 ? userAgent : userAgent.substring(0, 500);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public String getP256dh() {
		return p256dh;
	}

	public String getAuth() {
		return auth;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
