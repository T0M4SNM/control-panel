package io.github.t0m4snm.control_panel_backend.push;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;

/**
 * Claves VAPID del servidor (RFC 8292): identifican a esta aplicación ante el servicio de push del
 * navegador. La pública se da al navegador al suscribirse; con la privada se firma un JWT corto en
 * cada envío.
 *
 * @param publicKey punto P-256 sin comprimir en Base64 URL (el que pide {@code pushManager.subscribe})
 * @param subject contacto del responsable, {@code mailto:...} o {@code https://...}
 */
public record VapidKeys(String publicKey, ECPrivateKey privateKey, String subject) {

	/** Validez del JWT; el máximo que permite la especificación es 24 h. */
	static final Duration TOKEN_VALIDITY = Duration.ofHours(12);

	public static VapidKeys of(String publicKeyBase64, String privateKeyBase64, String subject)
			throws GeneralSecurityException {
		// Comprueba que la pública es un punto válido de P-256
		WebPushCrypto.decodePublic(WebPushCrypto.fromBase64Url(publicKeyBase64));
		return new VapidKeys(publicKeyBase64.trim(),
				WebPushCrypto.decodePrivate(WebPushCrypto.fromBase64Url(privateKeyBase64)), subject);
	}

	/** Valor de la cabecera {@code Authorization} para enviar a {@code endpoint}. */
	public String authorizationHeader(URI endpoint, Instant now) throws GeneralSecurityException {
		return "vapid t=" + jwt(audience(endpoint), now) + ", k=" + publicKey;
	}

	/** JWT ES256 con {@code aud} (origen del servicio de push), {@code exp} y {@code sub}. */
	String jwt(String audience, Instant now) throws GeneralSecurityException {
		String header = encodeJson("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
		String claims = encodeJson("{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}".formatted(audience,
				now.plus(TOKEN_VALIDITY).getEpochSecond(), subject));
		String signingInput = header + "." + claims;
		// P1363 da la firma como R || S (64 bytes), el formato de JWS; el normal de Java es DER
		Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
		signer.initSign(privateKey);
		signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
		return signingInput + "." + WebPushCrypto.base64Url(signer.sign());
	}

	static String audience(URI endpoint) {
		int port = endpoint.getPort();
		return endpoint.getScheme() + "://" + endpoint.getHost() + (port == -1 ? "" : ":" + port);
	}

	private static String encodeJson(String json) {
		return WebPushCrypto.base64Url(json.getBytes(StandardCharsets.UTF_8));
	}

}
