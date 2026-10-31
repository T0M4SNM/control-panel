package io.github.t0m4snm.control_panel_backend.auth;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import io.github.t0m4snm.control_panel_backend.user.AppUser;
import io.github.t0m4snm.control_panel_backend.user.UserRole;

/**
 * Usuario autenticado que se guarda en la sesión. Es una copia inmutable de {@link AppUser}
 * para no guardar entidades JPA en la sesión HTTP.
 */
public record AuthenticatedUser(
		UUID id,
		UUID workspaceId,
		String email,
		String displayName,
		UserRole role,
		String passwordHash,
		boolean active) implements UserDetails, Serializable {

	static AuthenticatedUser from(AppUser user) {
		return new AuthenticatedUser(user.getId(), user.getWorkspace().getId(), user.getEmail(),
				user.getDisplayName(), user.getRole(), user.getPasswordHash(), user.isActive());
	}

	public boolean isAdmin() {
		return role == UserRole.ADMIN;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return active;
	}

}
