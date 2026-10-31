package io.github.t0m4snm.control_panel_backend.monitoring;

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

/** Una ejecución de un {@link HealthCheck}. Se purgan a los 30 días. */
@Entity
@Table(name = "check_result")
public class CheckResult {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "health_check_id", nullable = false)
	private HealthCheck healthCheck;

	@Column(name = "checked_at", nullable = false)
	private Instant checkedAt;

	@Column(nullable = false)
	private boolean success;

	@Column(name = "response_ms")
	private Integer responseMs;

	/** Solo {@code HTTP}. */
	@Column(name = "status_code")
	private Integer statusCode;

	@Column(columnDefinition = "text")
	private String error;

	/** Solo {@code SSL}. */
	@Column(name = "ssl_expires_at")
	private Instant sslExpiresAt;

	protected CheckResult() {
	}

	public CheckResult(HealthCheck healthCheck, Instant checkedAt, boolean success, Integer responseMs,
			Integer statusCode, String error, Instant sslExpiresAt) {
		this.healthCheck = healthCheck;
		this.checkedAt = checkedAt;
		this.success = success;
		this.responseMs = responseMs;
		this.statusCode = statusCode;
		this.error = error;
		this.sslExpiresAt = sslExpiresAt;
	}

	public UUID getId() {
		return id;
	}

	public HealthCheck getHealthCheck() {
		return healthCheck;
	}

	public Instant getCheckedAt() {
		return checkedAt;
	}

	public boolean isSuccess() {
		return success;
	}

	public Integer getResponseMs() {
		return responseMs;
	}

	public Integer getStatusCode() {
		return statusCode;
	}

	public String getError() {
		return error;
	}

	public Instant getSslExpiresAt() {
		return sslExpiresAt;
	}

}
