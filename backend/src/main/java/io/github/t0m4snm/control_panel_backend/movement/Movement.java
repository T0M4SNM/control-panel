package io.github.t0m4snm.control_panel_backend.movement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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

import io.github.t0m4snm.control_panel_backend.contract.Contract;
import io.github.t0m4snm.control_panel_backend.project.Project;
import io.github.t0m4snm.control_panel_backend.user.Workspace;

/**
 * Ingreso o gasto real fuera de contrato. Los pagos de los contratos no se registran aquí: su coste
 * se calcula. Solo lo ve el {@code ADMIN}.
 */
@Entity
@Table(name = "movement")
public class Movement {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MovementType type;

	/** Siempre positivo; el signo lo da {@code type}. */
	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false)
	private LocalDate date;

	@Column(nullable = false, length = 500)
	private String description;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id")
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "contract_id")
	private Contract contract;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Recurrence recurrence = Recurrence.ONE_TIME;

	@Column(name = "ends_on")
	private LocalDate endsOn;

	protected Movement() {
	}

	public Movement(Workspace workspace) {
		this.workspace = workspace;
	}

	/**
	 * Fecha en la que el movimiento cuenta dentro de un mes, si cuenta. Un movimiento mensual del día
	 * 31 cuenta el último día de los meses más cortos.
	 */
	public Optional<LocalDate> occurrenceIn(YearMonth month) {
		LocalDate occurrence = switch (recurrence) {
			case ONE_TIME -> YearMonth.from(date).equals(month) ? date : null;
			case MONTHLY -> month.atDay(Math.min(date.getDayOfMonth(), month.lengthOfMonth()));
			case YEARLY -> month.getMonth() == date.getMonth()
					? month.atDay(Math.min(date.getDayOfMonth(), month.lengthOfMonth()))
					: null;
		};
		if (occurrence == null || occurrence.isBefore(date) || (endsOn != null && occurrence.isAfter(endsOn))) {
			return Optional.empty();
		}
		return Optional.of(occurrence);
	}

	/** Importe con signo: positivo si es ingreso, negativo si es gasto. */
	public BigDecimal signedAmount() {
		return type == MovementType.INCOME ? amount : amount.negate();
	}

	public UUID getId() {
		return id;
	}

	public Workspace getWorkspace() {
		return workspace;
	}

	public MovementType getType() {
		return type;
	}

	public void setType(MovementType type) {
		this.type = type;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Project getProject() {
		return project;
	}

	public void setProject(Project project) {
		this.project = project;
	}

	public Contract getContract() {
		return contract;
	}

	public void setContract(Contract contract) {
		this.contract = contract;
	}

	public Recurrence getRecurrence() {
		return recurrence;
	}

	public void setRecurrence(Recurrence recurrence) {
		this.recurrence = recurrence;
	}

	public LocalDate getEndsOn() {
		return endsOn;
	}

	public void setEndsOn(LocalDate endsOn) {
		this.endsOn = endsOn;
	}

}
