package io.github.t0m4snm.control_panel_backend.user;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.common.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

/**
 * Usuarios del workspace. Por ahora solo el admin los consulta y les fija la tarifa por hora; crear
 * e invitar usuarios llegará más adelante.
 */
@RestController
@RequestMapping("/api/users")
@Transactional
public class UserController {

	private final AppUserRepository users;

	public UserController(AppUserRepository users) {
		this.users = users;
	}

	@GetMapping
	@Transactional(readOnly = true)
	List<UserResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
		requireAdmin(user);
		return users.findByWorkspaceIdOrderByDisplayName(user.workspaceId()).stream().map(UserResponse::from).toList();
	}

	/**
	 * Tarifa por defecto del usuario. Solo afecta a los cronómetros que se inicien a partir de ahora:
	 * cada entrada guarda su propia copia.
	 */
	@PutMapping("/{userId}/hourly-rate")
	UserResponse setHourlyRate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID userId,
			@Valid @RequestBody HourlyRateRequest request) {
		requireAdmin(user);
		AppUser target = users.findById(userId)
				.filter(u -> u.getWorkspace().getId().equals(user.workspaceId()))
				.orElseThrow(ApiErrors::notFound);
		target.setHourlyRate(request.hourlyRate() == null ? null : Money.toCents(request.hourlyRate()));
		return UserResponse.from(target);
	}

	private static void requireAdmin(AuthenticatedUser user) {
		if (!user.isAdmin()) {
			throw ApiErrors.forbidden();
		}
	}

	record HourlyRateRequest(@DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal hourlyRate) {
	}

	record UserResponse(UUID id, String email, String displayName, UserRole role, boolean active,
			BigDecimal hourlyRate) {

		static UserResponse from(AppUser u) {
			return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.isActive(),
					u.getHourlyRate());
		}

	}

}
