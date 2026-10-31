package io.github.t0m4snm.control_panel_backend.cost;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;

@RestController
public class CostController {

	private final CostService service;

	public CostController(CostService service) {
		this.service = service;
	}

	@GetMapping("/api/costs/summary")
	CostSummaryResponse summary(@AuthenticationPrincipal AuthenticatedUser user) {
		return service.summary(user);
	}

}
