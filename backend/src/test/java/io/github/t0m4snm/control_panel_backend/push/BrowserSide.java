package io.github.t0m4snm.control_panel_backend.push;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Lo que hace el navegador al recibir un push: descifrar con su clave privada y su secreto. Sirve para
 * comprobar que lo que cifra el servidor se puede leer al otro lado.
 */
record BrowserSide(KeyPair keys, byte[] authSecret) {

	static BrowserSide create() throws GeneralSecurityException {
		byte[] auth = new byte[16];
		new SecureRandom().nextBytes(auth);
		return new BrowserSide(WebPushCrypto.generateKeyPair(), auth);
	}

	byte[] publicKey() {
		return WebPushCrypto.encodePublic((ECPublicKey) keys.getPublic());
	}

	String p256dh() {
		return WebPushCrypto.base64Url(publicKey());
	}

	String auth() {
		return WebPushCrypto.base64Url(authSecret);
	}

	/** Descifra un cuerpo {@code aes128gcm} de un solo registro y quita el delimitador final. */
	byte[] decrypt(byte[] body) throws GeneralSecurityException {
		ByteBuffer buffer = ByteBuffer.wrap(body);
		byte[] salt = new byte[16];
		buffer.get(salt);
		int recordSize = buffer.getInt();
		if (recordSize != WebPushCrypto.RECORD_SIZE) {
			throw new GeneralSecurityException("rs inesperado: " + recordSize);
		}
		byte[] asPublic = new byte[buffer.get()];
		buffer.get(asPublic);
		byte[] ciphertext = new byte[buffer.remaining()];
		buffer.get(ciphertext);

		byte[] ecdhSecret = WebPushCrypto.ecdh(keys.getPrivate(), WebPushCrypto.decodePublic(asPublic));
		byte[][] keyAndNonce = WebPushCrypto.deriveKeyAndNonce(ecdhSecret, authSecret, publicKey(), asPublic, salt);
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyAndNonce[0], "AES"),
				new GCMParameterSpec(128, keyAndNonce[1]));
		byte[] plaintext = cipher.doFinal(ciphertext);
		if (plaintext[plaintext.length - 1] != 2) {
			throw new GeneralSecurityException("Falta el delimitador de último registro");
		}
		return Arrays.copyOf(plaintext, plaintext.length - 1);
	}

}
