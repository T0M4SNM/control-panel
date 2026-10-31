package io.github.t0m4snm.control_panel_backend.apperror;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import io.github.t0m4snm.control_panel_backend.project.Project;

/** Un mismo error de una app (misma huella), con cuántas veces ha pasado y cuándo. */
@Entity
@Table(name = "error_group")
public class ErrorGroup {

	public enum Status {
		OPEN,
		RESOLVED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(nullable = false, length = 64)
	private String fingerprint;

	@Column(nullable = false, length = 200)
	private String type;

	/** El mensaje de la última vez. */
	@Column(nullable = false, columnDefinition = "text")
	private String message;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status = Status.OPEN;

	@Column(nullable = false)
	private long occurrences;

	@Column(name = "first_seen_at", nullable = false)
	private Instant firstSeenAt;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	protected ErrorGroup() {
	}

	public ErrorGroup(Project project, String fingerprint, String type, String message, Instant at) {
		this.project = project;
		this.fingerprint = fingerprint;
		this.type = type;
		this.message = message;
		this.firstSeenAt = at;
		this.lastSeenAt = at;
	}

	/**
	 * Registra una ocurrencia. Devuelve true si el grupo estaba resuelto y vuelve a abrirse (el error
	 * ha vuelto: hay que avisar).
	 */
	public boolean occurred(String latestMessage, Instant at) {
		occurrences++;
		message = latestMessage;
		if (at.isAfter(lastSeenAt)) {
			lastSeenAt = at;
		}
		if (status == Status.RESOLVED) {
			status = Status.OPEN;
			resolvedAt = null;
			return true;
		}
		return false;
	}

	public void resolve(Instant now) {
		status = Status.RESOLVED;
		resolvedAt = now;
	}

	public void reopen() {
		status = Status.OPEN;
		resolvedAt = null;
	}

	public UUID getId() {
		return id;
	}

	public Project getProject() {
		return project;
	}

	public String getFingerprint() {
		return fingerprint;
	}

	public String getType() {
		return type;
	}

	public String getMessage() {
		return message;
	}

	public Status getStatus() {
		return status;
	}

	public long getOccurrences() {
		return occurrences;
	}

	public Instant getFirstSeenAt() {
		return firstSeenAt;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

}
