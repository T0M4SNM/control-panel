package io.github.t0m4snm.control_panel_backend.notification;

import java.util.UUID;

/**
 * Aviso por enviar, igual para todos sus destinatarios.
 *
 * @param dedupeKey identifica el aviso: si un usuario ya tiene uno con la misma clave, no se repite
 */
public record NotificationDraft(NotificationType type, Severity severity, String title, String message,
		UUID contractId, UUID healthCheckId, String dedupeKey) {
}
