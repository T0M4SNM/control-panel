package io.github.t0m4snm.control_panel_backend.finance;

import java.time.YearMonth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;

@RestController
public class FinanceController {

	private final FinanceService service;

	public FinanceController(FinanceService service) {
		this.service = service;
	}

	/** {@code from} y {@code to} en formato {@code 2026-01}; por defecto, el año en curso. */
	@GetMapping("/api/finance/summary")
	FinanceSummaryResponse summary(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) YearMonth from, @RequestParam(required = false) YearMonth to) {
		return service.summary(user, from, to);
	}

}
