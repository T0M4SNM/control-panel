package io.github.t0m4snm.control_panel_backend.dashboard;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;

@RestController
public class DashboardController {

	private final DashboardService service;

	public DashboardController(DashboardService service) {
		this.service = service;
	}

	@GetMapping("/api/dashboard")
	DashboardResponse summary(@AuthenticationPrincipal AuthenticatedUser user) {
		return service.summary(user);
	}

}
