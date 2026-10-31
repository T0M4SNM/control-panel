package io.github.t0m4snm.control_panel_backend.push;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.github.t0m4snm.control_panel_backend.notification.NotificationWriter.NotificationsCreated;

/**
 * Envía por Web Push los avisos recién creados, cuando la transacción que los creó ya se ha
 * confirmado (si se deshace, no se envía nada). El envío va en un hilo virtual aparte: hablar con
 * los servicios de push no retrasa la comprobación ni la tarea que generó el aviso.
 */
@Component
class PushDispatcher {

	private final PushService push;

	PushDispatcher(PushService push) {
		this.push = push;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onNotificationsCreated(NotificationsCreated event) {
		Thread.ofVirtual().name("web-push").start(() -> push.deliver(event.notificationIds()));
	}

}
