package io.github.t0m4snm.control_panel_backend.auth;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.t0m4snm.control_panel_backend.user.UserRole;

/**
 * Login y logout los gestiona Spring Security (ver {@link SecurityConfig}); aquí solo están
 * los endpoints de apoyo para el frontend.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	/** Fuerza a crear la cookie {@code XSRF-TOKEN}; el frontend lo llama antes del login. */
	@GetMapping("/csrf")
	ResponseEntity<Void> csrf(CsrfToken token) {
		token.getToken();
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
		return new CurrentUserResponse(user.id(), user.workspaceId(), user.email(), user.displayName(), user.role());
	}

	record CurrentUserResponse(UUID id, UUID workspaceId, String email, String displayName, UserRole role) {
	}

}
