package io.github.t0m4snm.control_panel_backend.notification;

import java.util.UUID;

/**
 * Aviso por enviar, igual para todos sus destinatarios.
 *
 * @param dedupeKey identifica el aviso: si un usuario ya tiene uno con la misma clave, no se repite
 * @param errorGroupId grupo de error de una app, solo en los avisos {@code APP_ERROR}
 */
public record NotificationDraft(NotificationType type, Severity severity, String title, String message,
		UUID contractId, UUID healthCheckId, String dedupeKey, UUID errorGroupId) {

	/** Aviso sin grupo de error (vencimientos y comprobaciones). */
	public NotificationDraft(NotificationType type, Severity severity, String title, String message, UUID contractId,
			UUID healthCheckId, String dedupeKey) {
		this(type, severity, title, message, contractId, healthCheckId, dedupeKey, null);
	}

}
