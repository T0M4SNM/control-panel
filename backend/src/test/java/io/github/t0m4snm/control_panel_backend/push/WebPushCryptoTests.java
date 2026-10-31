package io.github.t0m4snm.control_panel_backend.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** Cifrado de RFC 8291 y firma VAPID de RFC 8292, sin Spring. */
class WebPushCryptoTests {

	@Test
	void browserCanDecryptWhatTheServerEncrypts() throws Exception {
		BrowserSide browser = BrowserSide.create();
		byte[] payload = "{\"title\":\"Caída: Web\",\"body\":\"No responde\"}".getBytes(StandardCharsets.UTF_8);

		byte[] body = WebPushCrypto.encrypt(payload, browser.publicKey(), browser.authSecret());

		assertThat(browser.decrypt(body)).isEqualTo(payload);
	}

	@Test
	void eachMessageUsesAFreshKeyAndSalt() throws Exception {
		BrowserSide browser = BrowserSide.create();
		byte[] payload = "hola".getBytes(StandardCharsets.UTF_8);

		assertThat(WebPushCrypto.encrypt(payload, browser.publicKey(), browser.authSecret()))
				.isNotEqualTo(WebPushCrypto.encrypt(payload, browser.publicKey(), browser.authSecret()));
	}

	/** Ejemplo del apéndice A de RFC 8291: mismas entradas, misma salida byte a byte. */
	@Test
	void matchesTheRfc8291Example() throws Exception {
		byte[] plaintext = b64("V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24");
		ECPrivateKey asPrivate = WebPushCrypto.decodePrivate(b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"));
		ECPublicKey asPublic = WebPushCrypto.decodePublic(
				b64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"));
		byte[] uaPublic = b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
		byte[] authSecret = b64("BTBZMqHH6r4Tts7J_aSIgg");
		byte[] salt = b64("DGv6ra1nlYgDCS1FRnbzlw");

		byte[] body = WebPushCrypto.encrypt(plaintext, uaPublic, authSecret, new KeyPair(asPublic, asPrivate), salt);

		assertThat(WebPushCrypto.base64Url(body)).isEqualTo(
				"DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK"
						+ "6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
	}

	@Test
	void keysSurviveTheWebFormat() throws Exception {
		KeyPair pair = WebPushCrypto.generateKeyPair();
		byte[] publicRaw = WebPushCrypto.encodePublic((ECPublicKey) pair.getPublic());
		byte[] privateRaw = WebPushCrypto.encodePrivate((ECPrivateKey) pair.getPrivate());

		assertThat(publicRaw).hasSize(65).startsWith((byte) 4);
		assertThat(privateRaw).hasSize(32);
		assertThat(WebPushCrypto.decodePublic(publicRaw)).isEqualTo(pair.getPublic());
		assertThat(WebPushCrypto.decodePrivate(privateRaw).getS()).isEqualTo(((ECPrivateKey) pair.getPrivate()).getS());
	}

	@Test
	void vapidJwtIsSignedWithTheServerKey() throws Exception {
		KeyPair pair = WebPushCrypto.generateKeyPair();
		VapidKeys vapid = VapidKeys.of(WebPushCrypto.base64Url(WebPushCrypto.encodePublic((ECPublicKey) pair.getPublic())),
				WebPushCrypto.base64Url(WebPushCrypto.encodePrivate((ECPrivateKey) pair.getPrivate())),
				"mailto:admin@example.com");
		Instant now = Instant.parse("2026-10-31T10:00:00Z");

		String header = vapid.authorizationHeader(URI.create("https://fcm.googleapis.com/fcm/send/abc"), now);

		assertThat(header).startsWith("vapid t=").endsWith(", k=" + vapid.publicKey());
		String jwt = header.substring("vapid t=".length(), header.indexOf(','));
		String[] parts = jwt.split("\\.");
		String claims = new String(b64(parts[1]), StandardCharsets.UTF_8);
		assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"")
				.contains("\"sub\":\"mailto:admin@example.com\"")
				.contains("\"exp\":" + now.plus(VapidKeys.TOKEN_VALIDITY).getEpochSecond());

		Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
		verifier.initVerify(pair.getPublic());
		verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
		assertThat(verifier.verify(b64(parts[2]))).isTrue();
	}

	private static byte[] b64(String value) {
		return WebPushCrypto.fromBase64Url(value);
	}

}
