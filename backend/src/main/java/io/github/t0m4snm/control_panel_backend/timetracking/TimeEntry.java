package io.github.t0m4snm.control_panel_backend.timetracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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

import io.github.t0m4snm.control_panel_backend.common.Money;
import io.github.t0m4snm.control_panel_backend.project.Project;
import io.github.t0m4snm.control_panel_backend.user.AppUser;

/**
 * Tiempo dedicado a un proyecto. Mientras el cronómetro corre, {@code endedAt} es nulo; la base
 * garantiza que cada usuario tenga como mucho uno en marcha. La duración no se guarda: se calcula.
 */
@Entity
@Table(name = "time_entry")
public class TimeEntry {

	private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	/** Copia de la tarifa del usuario al iniciar, para que cambiarla no reescriba el histórico. */
	@Column(name = "hourly_rate", precision = 8, scale = 2)
	private BigDecimal hourlyRate;

	@Column(length = 500)
	private String description;

	protected TimeEntry() {
	}

	public TimeEntry(AppUser user, Project project, Instant startedAt) {
		this.user = user;
		this.project = project;
		this.startedAt = startedAt;
		this.hourlyRate = user.getHourlyRate();
	}

	public boolean isRunning() {
		return endedAt == null;
	}

	/** Duración hasta el final o, si sigue en marcha, hasta {@code now}. */
	public Duration duration(Instant now) {
		return Duration.between(startedAt, endedAt == null ? now : endedAt);
	}

	/** Coste con decimales de cálculo: horas por tarifa. Cero si no tiene tarifa. */
	public BigDecimal cost(Instant now) {
		if (hourlyRate == null) {
			return BigDecimal.ZERO;
		}
		return hourlyRate.multiply(BigDecimal.valueOf(duration(now).toSeconds()))
				.divide(SECONDS_PER_HOUR, Money.CALCULATION_SCALE, RoundingMode.HALF_UP);
	}

	public UUID getId() {
		return id;
	}

	public AppUser getUser() {
		return user;
	}

	public Project getProject() {
		return project;
	}

	public void setProject(Project project) {
		this.project = project;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public void setEndedAt(Instant endedAt) {
		this.endedAt = endedAt;
	}

	public BigDecimal getHourlyRate() {
		return hourlyRate;
	}

	public void setHourlyRate(BigDecimal hourlyRate) {
		this.hourlyRate = hourlyRate;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

}
