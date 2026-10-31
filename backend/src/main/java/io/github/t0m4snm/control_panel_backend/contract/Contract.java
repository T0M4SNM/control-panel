package io.github.t0m4snm.control_panel_backend.contract;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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

import io.github.t0m4snm.control_panel_backend.common.Money;
import io.github.t0m4snm.control_panel_backend.user.Workspace;

@Entity
@Table(name = "contract")
public class Contract {

	private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(nullable = false, length = 100)
	private String provider;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ContractType type;

	/** Importe total por periodo, en euros, sin desglose de IVA. */
	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(name = "billing_period", nullable = false, length = 20)
	private BillingPeriod billingPeriod;

	@Column(name = "renewal_date", nullable = false)
	private LocalDate renewalDate;

	@Column(name = "auto_renew", nullable = false)
	private boolean autoRenew = true;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ContractStatus status = ContractStatus.ACTIVE;

	@Column(columnDefinition = "text")
	private String notes;

	protected Contract() {
	}

	public Contract(Workspace workspace) {
		this.workspace = workspace;
	}

	/** Coste mensual equivalente, con decimales de cálculo: un contrato anual se reparte en 12 meses. */
	public BigDecimal monthlyAmount() {
		return switch (billingPeriod) {
			case MONTHLY -> amount.setScale(Money.CALCULATION_SCALE);
			case YEARLY -> amount.divide(MONTHS_PER_YEAR, Money.CALCULATION_SCALE, RoundingMode.HALF_UP);
		};
	}

	public boolean isActive() {
		return status == ContractStatus.ACTIVE;
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

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public ContractType getType() {
		return type;
	}

	public void setType(ContractType type) {
		this.type = type;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public BillingPeriod getBillingPeriod() {
		return billingPeriod;
	}

	public void setBillingPeriod(BillingPeriod billingPeriod) {
		this.billingPeriod = billingPeriod;
	}

	public LocalDate getRenewalDate() {
		return renewalDate;
	}

	public void setRenewalDate(LocalDate renewalDate) {
		this.renewalDate = renewalDate;
	}

	public boolean isAutoRenew() {
		return autoRenew;
	}

	public void setAutoRenew(boolean autoRenew) {
		this.autoRenew = autoRenew;
	}

	public ContractStatus getStatus() {
		return status;
	}

	public void setStatus(ContractStatus status) {
		this.status = status;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

}
