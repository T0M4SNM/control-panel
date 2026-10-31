package io.github.t0m4snm.control_panel_backend.ingest;

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

import io.github.t0m4snm.control_panel_backend.project.Project;
import io.github.t0m4snm.control_panel_backend.server.Server;
import io.github.t0m4snm.control_panel_backend.user.Workspace;

/**
 * Clave con la que una app (proyecto) o un servidor envía datos al panel. Solo se guarda el hash; la
 * clave en claro se muestra una única vez, al crearla. Revocarla la invalida para siempre.
 */
@Entity
@Table(name = "ingest_key")
public class IngestKey {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id")
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "server_id")
	private Server server;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "key_hash", nullable = false, unique = true, length = 64)
	private String keyHash;

	/** Primeros caracteres de la clave, para reconocerla en la lista sin poder usarla. */
	@Column(name = "key_prefix", nullable = false, length = 12)
	private String keyPrefix;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected IngestKey() {
	}

	public IngestKey(Workspace workspace, Project project, Server server, String name, String keyHash, String keyPrefix) {
		this.workspace = workspace;
		this.project = project;
		this.server = server;
		this.name = name;
		this.keyHash = keyHash;
		this.keyPrefix = keyPrefix;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public void revoke(Instant now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}

	/** Se actualiza como mucho una vez por minuto, para no escribir en cada envío. */
	public void touch(Instant now) {
		if (lastUsedAt == null || lastUsedAt.isBefore(now.minusSeconds(60))) {
			lastUsedAt = now;
		}
	}

	public UUID getId() {
		return id;
	}

	public Workspace getWorkspace() {
		return workspace;
	}

	public Project getProject() {
		return project;
	}

	public Server getServer() {
		return server;
	}

	public String getName() {
		return name;
	}

	public String getKeyPrefix() {
		return keyPrefix;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastUsedAt() {
		return lastUsedAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

}
