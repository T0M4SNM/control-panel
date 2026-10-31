package io.github.t0m4snm.control_panel_backend.project;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import io.github.t0m4snm.control_panel_backend.user.AppUser;

@Entity
@Table(name = "project_member")
public class ProjectMember {

	@EmbeddedId
	private Id id;

	@MapsId("projectId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id")
	private Project project;

	@MapsId("userId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private AppUser user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProjectRole role;

	@Column(name = "added_at", nullable = false, updatable = false)
	private Instant addedAt = Instant.now();

	protected ProjectMember() {
	}

	public ProjectMember(Project project, AppUser user, ProjectRole role) {
		this.id = new Id(project.getId(), user.getId());
		this.project = project;
		this.user = user;
		this.role = role;
	}

	public Project getProject() {
		return project;
	}

	public AppUser getUser() {
		return user;
	}

	public ProjectRole getRole() {
		return role;
	}

	public void setRole(ProjectRole role) {
		this.role = role;
	}

	public Instant getAddedAt() {
		return addedAt;
	}

	/** Clave compuesta (project_id, user_id). */
	@Embeddable
	public record Id(
			@Column(name = "project_id") UUID projectId,
			@Column(name = "user_id") UUID userId) implements Serializable {
	}

}
