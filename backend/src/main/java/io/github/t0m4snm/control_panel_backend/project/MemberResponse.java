package io.github.t0m4snm.control_panel_backend.project;

import java.time.Instant;
import java.util.UUID;

import io.github.t0m4snm.control_panel_backend.user.AppUser;

public record MemberResponse(UUID userId, String displayName, String email, ProjectRole role, Instant addedAt) {

	static MemberResponse from(ProjectMember membership) {
		AppUser user = membership.getUser();
		return new MemberResponse(user.getId(), user.getDisplayName(), user.getEmail(), membership.getRole(),
				membership.getAddedAt());
	}

}
