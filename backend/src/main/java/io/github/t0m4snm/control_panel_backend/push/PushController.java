package io.github.t0m4snm.control_panel_backend.push;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Suscripción del dispositivo actual a Web Push. Cualquier usuario puede suscribir los suyos. */
@RestController
@RequestMapping("/api/push")
public class PushController {

	private final PushService push;

	public PushController(PushService push) {
		this.push = push;
	}

	/** Lo que necesita el frontend para suscribirse; {@code enabled=false} si el servidor no tiene claves. */
	@GetMapping("/config")
	PushConfig config(@AuthenticationPrincipal AuthenticatedUser user) {
		return new PushConfig(push.publicKey().isPresent(), push.publicKey().orElse(null),
				push.subscriptionCount(user));
	}

	/** Recibe tal cual el {@code PushSubscription.toJSON()} del navegador. */
	@PostMapping("/subscriptions")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void subscribe(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody SubscriptionRequest request,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
		push.subscribe(user, request.endpoint(), request.keys().p256dh(), request.keys().auth(), userAgent);
	}

	@PostMapping("/subscriptions/unsubscribe")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void unsubscribe(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody UnsubscribeRequest request) {
		push.unsubscribe(user, request.endpoint());
	}

	record PushConfig(boolean enabled, String publicKey, long subscriptions) {
	}

	record SubscriptionRequest(@NotBlank String endpoint, @NotNull @Valid Keys keys) {

		record Keys(@NotBlank String p256dh, @NotBlank String auth) {
		}

	}

	record UnsubscribeRequest(@NotBlank String endpoint) {
	}

}
