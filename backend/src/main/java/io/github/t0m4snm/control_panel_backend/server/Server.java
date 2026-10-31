package io.github.t0m4snm.control_panel_backend.server;

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

import io.github.t0m4snm.control_panel_backend.contract.Contract;
import io.github.t0m4snm.control_panel_backend.user.Workspace;

/** Servidor del workspace. Nunca se guardan contraseñas ni claves. */
@Entity
@Table(name = "server")
public class Server {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "contract_id")
	private Contract contract;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(length = 100)
	private String provider;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	@Column(length = 100)
	private String os;

	@Column(name = "cpu_cores")
	private Integer cpuCores;

	@Column(name = "ram_mb")
	private Integer ramMb;

	@Column(name = "disk_gb")
	private Integer diskGb;

	@Column(columnDefinition = "text")
	private String notes;

	protected Server() {
	}

	public Server(Workspace workspace) {
		this.workspace = workspace;
	}

	public UUID getId() {
		return id;
	}

	public Workspace getWorkspace() {
		return workspace;
	}

	public Contract getContract() {
		return contract;
	}

	public void setContract(Contract contract) {
		this.contract = contract;
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

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public String getOs() {
		return os;
	}

	public void setOs(String os) {
		this.os = os;
	}

	public Integer getCpuCores() {
		return cpuCores;
	}

	public void setCpuCores(Integer cpuCores) {
		this.cpuCores = cpuCores;
	}

	public Integer getRamMb() {
		return ramMb;
	}

	public void setRamMb(Integer ramMb) {
		this.ramMb = ramMb;
	}

	public Integer getDiskGb() {
		return diskGb;
	}

	public void setDiskGb(Integer diskGb) {
		this.diskGb = diskGb;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

}
