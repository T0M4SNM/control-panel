package io.github.t0m4snm.control_panel_backend.monitoring;

import java.time.Instant;
import java.util.Optional;
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

import io.github.t0m4snm.control_panel_backend.domain.Domain;
import io.github.t0m4snm.control_panel_backend.project.Project;
import io.github.t0m4snm.control_panel_backend.server.Server;
import io.github.t0m4snm.control_panel_backend.user.Workspace;

/**
 * Comprobación de estado que se ejecuta cada {@code intervalSeconds}. Pasa a {@code DOWN} tras
 * {@code failureThreshold} fallos seguidos y vuelve a {@code UP} con el primer éxito.
 */
@Entity
@Table(name = "health_check")
public class HealthCheck {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	@Column(nullable = false, length = 150)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CheckType type;

	/** URL para {@code HTTP}; host o IP para el resto. */
	@Column(nullable = false, length = 500)
	private String target;

	private Integer port;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "server_id")
	private Server server;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "domain_id")
	private Domain domain;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id")
	private Project project;

	@Column(name = "interval_seconds", nullable = false)
	private int intervalSeconds = 300;

	@Column(name = "timeout_ms", nullable = false)
	private int timeoutMs = 10_000;

	@Column(name = "failure_threshold", nullable = false)
	private int failureThreshold = 3;

	@Column(nullable = false)
	private boolean enabled = true;

	@Enumerated(EnumType.STRING)
	@Column(name = "current_status", nullable = false, length = 20)
	private CheckStatus currentStatus = CheckStatus.UNKNOWN;

	@Column(name = "consecutive_failures", nullable = false)
	private int consecutiveFailures;

	@Column(name = "last_checked_at")
	private Instant lastCheckedAt;

	@Column(name = "status_changed_at")
	private Instant statusChangedAt;

	protected HealthCheck() {
	}

	public HealthCheck(Workspace workspace) {
		this.workspace = workspace;
	}

	/**
	 * Aplica el resultado de una ejecución y devuelve el estado anterior si ha cambiado.
	 * Un éxito pone el estado en {@code UP}; los fallos solo lo pasan a {@code DOWN} al llegar al umbral.
	 */
	public Optional<CheckStatus> record(boolean success, Instant checkedAt) {
		lastCheckedAt = checkedAt;
		CheckStatus previous = currentStatus;
		if (success) {
			consecutiveFailures = 0;
			currentStatus = CheckStatus.UP;
		} else {
			consecutiveFailures++;
			if (consecutiveFailures >= failureThreshold) {
				currentStatus = CheckStatus.DOWN;
			}
		}
		if (currentStatus == previous) {
			return Optional.empty();
		}
		statusChangedAt = checkedAt;
		return Optional.of(previous);
	}

	/** Al cambiar qué se comprueba, el estado anterior deja de valer. */
	public void resetStatus() {
		currentStatus = CheckStatus.UNKNOWN;
		consecutiveFailures = 0;
		lastCheckedAt = null;
		statusChangedAt = null;
	}

	public UUID getId() {
		return id;
	}

	public Workspace getWorkspace() {
		return workspace;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public CheckType getType() {
		return type;
	}

	public void setType(CheckType type) {
		this.type = type;
	}

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public Server getServer() {
		return server;
	}

	public void setServer(Server server) {
		this.server = server;
	}

	public Domain getDomain() {
		return domain;
	}

	public void setDomain(Domain domain) {
		this.domain = domain;
	}

	public Project getProject() {
		return project;
	}

	public void setProject(Project project) {
		this.project = project;
	}

	public int getIntervalSeconds() {
		return intervalSeconds;
	}

	public void setIntervalSeconds(int intervalSeconds) {
		this.intervalSeconds = intervalSeconds;
	}

	public int getTimeoutMs() {
		return timeoutMs;
	}

	public void setTimeoutMs(int timeoutMs) {
		this.timeoutMs = timeoutMs;
	}

	public int getFailureThreshold() {
		return failureThreshold;
	}

	public void setFailureThreshold(int failureThreshold) {
		this.failureThreshold = failureThreshold;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public CheckStatus getCurrentStatus() {
		return currentStatus;
	}

	public int getConsecutiveFailures() {
		return consecutiveFailures;
	}

	public Instant getLastCheckedAt() {
		return lastCheckedAt;
	}

	public Instant getStatusChangedAt() {
		return statusChangedAt;
	}

}
