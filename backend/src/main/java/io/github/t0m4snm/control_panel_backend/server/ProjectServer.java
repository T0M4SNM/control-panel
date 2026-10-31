package io.github.t0m4snm.control_panel_backend.server;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import io.github.t0m4snm.control_panel_backend.project.Project;

/** Un proyecto usa un servidor y carga con {@code costSharePercent} % de su coste. */
@Entity
@Table(name = "project_server")
public class ProjectServer {

	@EmbeddedId
	private Id id;

	@MapsId("projectId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id")
	private Project project;

	@MapsId("serverId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "server_id")
	private Server server;

	@Column(name = "cost_share_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal costSharePercent;

	protected ProjectServer() {
	}

	public ProjectServer(Project project, Server server, BigDecimal costSharePercent) {
		this.id = new Id(project.getId(), server.getId());
		this.project = project;
		this.server = server;
		this.costSharePercent = costSharePercent;
	}

	public Project getProject() {
		return project;
	}

	public Server getServer() {
		return server;
	}

	public BigDecimal getCostSharePercent() {
		return costSharePercent;
	}

	public void setCostSharePercent(BigDecimal costSharePercent) {
		this.costSharePercent = costSharePercent;
	}

	/** Clave compuesta (project_id, server_id). */
	@Embeddable
	public record Id(
			@Column(name = "project_id") UUID projectId,
			@Column(name = "server_id") UUID serverId) implements Serializable {
	}

}
