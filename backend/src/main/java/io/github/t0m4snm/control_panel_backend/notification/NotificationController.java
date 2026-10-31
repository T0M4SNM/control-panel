package io.github.t0m4snm.control_panel_backend.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;

/** Panel de avisos del usuario actual. Cada uno solo ve los suyos. */
@RestController
@RequestMapping("/api/notifications")
@Transactional
public class NotificationController {

	static final int DEFAULT_LIMIT = 50;

	static final int MAX_LIMIT = 200;

	private final NotificationRepository notifications;

	private final Clock clock;

	public NotificationController(NotificationRepository notifications, Clock clock) {
		this.notifications = notifications;
		this.clock = clock;
	}

	/** Del más reciente al más antiguo; con {@code unreadOnly=true}, solo los no leídos. */
	@GetMapping
	@Transactional(readOnly = true)
	List<NotificationResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(defaultValue = "false") boolean unreadOnly,
			@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
		if (limit < 1 || limit > MAX_LIMIT) {
			throw ApiErrors.badRequest("limit debe estar entre 1 y " + MAX_LIMIT);
		}
		List<Notification> found = unreadOnly
				? notifications.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(user.id(), Limit.of(limit))
				: notifications.findByUserIdOrderByCreatedAtDesc(user.id(), Limit.of(limit));
		return found.stream().map(NotificationResponse::from).toList();
	}

	/** Para el contador de la campana. */
	@GetMapping("/unread-count")
	@Transactional(readOnly = true)
	UnreadCount unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
		return new UnreadCount(notifications.countByUserIdAndReadAtIsNull(user.id()));
	}

	@PostMapping("/{notificationId}/read")
	NotificationResponse markRead(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID notificationId) {
		Notification notification = notifications.findByIdAndUserId(notificationId, user.id())
				.orElseThrow(ApiErrors::notFound);
		notification.markRead(clock.instant());
		return NotificationResponse.from(notification);
	}

	@PostMapping("/read-all")
	UnreadCount markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
		notifications.markAllRead(user.id(), clock.instant());
		return new UnreadCount(0);
	}

	record UnreadCount(long count) {
	}

	record NotificationResponse(UUID id, NotificationType type, Severity severity, String title, String message,
			UUID contractId, UUID healthCheckId, UUID errorGroupId, Instant createdAt, Instant readAt) {

		static NotificationResponse from(Notification n) {
			return new NotificationResponse(n.getId(), n.getType(), n.getSeverity(), n.getTitle(), n.getMessage(),
					n.getContractId(), n.getHealthCheckId(), n.getErrorGroupId(), n.getCreatedAt(), n.getReadAt());
		}

	}

}
