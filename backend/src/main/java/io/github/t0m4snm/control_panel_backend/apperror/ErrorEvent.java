package io.github.t0m4snm.control_panel_backend.apperror;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Una vez que ocurrió un error. Se purgan a los 30 días; el grupo se queda. */
@Entity
@Table(name = "error_event")
public class ErrorEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	private ErrorGroup group;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(nullable = false, columnDefinition = "text")
	private String message;

	@Column(columnDefinition = "text")
	private String stack;

	@Column(length = 50)
	private String environment;

	@Column(length = 100)
	private String release;

	/** Objeto JSON con datos de la petición (URL, método...), ya serializado. */
	@Column(columnDefinition = "text")
	private String context;

	protected ErrorEvent() {
	}

	public ErrorEvent(ErrorGroup group, Instant occurredAt, String message, String stack, String environment,
			String release, String context) {
		this.group = group;
		this.occurredAt = occurredAt;
		this.message = message;
		this.stack = stack;
		this.environment = environment;
		this.release = release;
		this.context = context;
	}

	public UUID getId() {
		return id;
	}

	public ErrorGroup getGroup() {
		return group;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public String getMessage() {
		return message;
	}

	public String getStack() {
		return stack;
	}

	public String getEnvironment() {
		return environment;
	}

	public String getRelease() {
		return release;
	}

	public String getContext() {
		return context;
	}

}
