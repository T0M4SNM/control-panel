package io.github.t0m4snm.control_panel_backend.push;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de Web Push (ver {@code .env.example}). Sin claves VAPID, Web Push queda desactivado
 * y los avisos solo se ven en el panel.
 *
 * @param vapidPublicKey clave pública VAPID en Base64 URL
 * @param vapidPrivateKey clave privada VAPID en Base64 URL; secreta, nunca en el repo
 * @param subject contacto que se envía a los servicios de push ({@code mailto:...})
 * @param allowedEndpointHosts servicios de push a los que se permite enviar: el host del
 * {@code endpoint} debe ser uno de estos o un subdominio
 * @param allowHttp permite endpoints {@code http://}; solo para tests
 */
@ConfigurationProperties("app.push")
public record PushProperties(
		String vapidPublicKey,
		String vapidPrivateKey,
		@DefaultValue("mailto:admin@localhost") String subject,
		@DefaultValue({ "fcm.googleapis.com", "push.services.mozilla.com", "push.apple.com",
				"notify.windows.com" }) List<String> allowedEndpointHosts,
		@DefaultValue("false") boolean allowHttp) {

	boolean hasVapidKeys() {
		return vapidPublicKey != null && !vapidPublicKey.isBlank() && vapidPrivateKey != null
				&& !vapidPrivateKey.isBlank();
	}

}
