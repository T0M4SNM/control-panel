package io.github.t0m4snm.control_panel_backend.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Aviso para un usuario. Se crea con {@link NotificationWriter} (SQL directo, para que no se repita);
 * desde aquí solo se lee y se marca como leído.
 */
@Entity
@Table(name = "notification")
public class Notification {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30, updatable = false)
	private NotificationType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private Severity severity;

	@Column(nullable = false, length = 200, updatable = false)
	private String title;

	@Column(nullable = false, columnDefinition = "text", updatable = false)
	private String message;

	@Column(name = "contract_id", updatable = false)
	private UUID contractId;

	@Column(name = "health_check_id", updatable = false)
	private UUID healthCheckId;

	@Column(name = "dedupe_key", nullable = false, updatable = false)
	private String dedupeKey;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "read_at")
	private Instant readAt;

	protected Notification() {
	}

	public void markRead(Instant now) {
		if (readAt == null) {
			readAt = now;
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public NotificationType getType() {
		return type;
	}

	public Severity getSeverity() {
		return severity;
	}

	public String getTitle() {
		return title;
	}

	public String getMessage() {
		return message;
	}

	public UUID getContractId() {
		return contractId;
	}

	public UUID getHealthCheckId() {
		return healthCheckId;
	}

	public String getDedupeKey() {
		return dedupeKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getReadAt() {
		return readAt;
	}

}
