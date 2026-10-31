package io.github.t0m4snm.control_panel_backend.push;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Criptografía de Web Push solo con el JDK, sin librerías externas:
 * <ul>
 * <li>Cifrado del contenido con {@code aes128gcm} (RFC 8291 y RFC 8188): ECDH con la clave del
 * navegador, HKDF para derivar clave y nonce, y AES-128-GCM.</li>
 * <li>Claves P-256 en el formato de la web: punto sin comprimir (65 bytes) y privada de 32 bytes, en
 * Base64 URL sin relleno.</li>
 * </ul>
 */
public final class WebPushCrypto {

	/** Tamaño de registro de RFC 8188; el mensaje cabe en uno solo. */
	static final int RECORD_SIZE = 4096;

	private static final Base64.Encoder B64_ENCODER = Base64.getUrlEncoder().withoutPadding();

	private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final ECParameterSpec P256 = p256();

	private WebPushCrypto() {
	}

	/**
	 * Cifra {@code payload} para una suscripción.
	 *
	 * @param uaPublic clave pública del navegador ({@code p256dh}), 65 bytes
	 * @param authSecret secreto de autenticación del navegador ({@code auth}), 16 bytes
	 * @return cuerpo de la petición: cabecera {@code aes128gcm} + texto cifrado
	 */
	public static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] authSecret) throws GeneralSecurityException {
		KeyPair ephemeral = generateKeyPair();
		byte[] salt = new byte[16];
		RANDOM.nextBytes(salt);
		return encrypt(payload, uaPublic, authSecret, ephemeral, salt);
	}

	/** Igual que {@link #encrypt(byte[], byte[], byte[])} con clave efímera y sal fijas (para tests). */
	static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] authSecret, KeyPair ephemeral, byte[] salt)
			throws GeneralSecurityException {
		byte[] asPublic = encodePublic((ECPublicKey) ephemeral.getPublic());
		byte[] ecdhSecret = ecdh(ephemeral.getPrivate(), decodePublic(uaPublic));
		byte[][] keyAndNonce = deriveKeyAndNonce(ecdhSecret, authSecret, uaPublic, asPublic, salt);

		// Un solo registro: contenido seguido del delimitador 0x02 de «último registro», sin relleno
		byte[] plaintext = Arrays.copyOf(payload, payload.length + 1);
		plaintext[payload.length] = 2;
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyAndNonce[0], "AES"),
				new GCMParameterSpec(128, keyAndNonce[1]));
		byte[] ciphertext = cipher.doFinal(plaintext);

		return ByteBuffer.allocate(16 + 4 + 1 + asPublic.length + ciphertext.length)
				.put(salt)
				.putInt(RECORD_SIZE)
				.put((byte) asPublic.length)
				.put(asPublic)
				.put(ciphertext)
				.array();
	}

	/**
	 * Deriva la clave de contenido (16 bytes) y el nonce (12 bytes) según RFC 8291, sección 3.4. Es la
	 * misma derivación que hace el navegador para descifrar.
	 */
	static byte[][] deriveKeyAndNonce(byte[] ecdhSecret, byte[] authSecret, byte[] uaPublic, byte[] asPublic,
			byte[] salt) throws GeneralSecurityException {
		byte[] prkKey = hmac(authSecret, ecdhSecret);
		byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic,
				new byte[] { 1 });
		byte[] ikm = hmac(prkKey, keyInfo);

		byte[] prk = hmac(salt, ikm);
		byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII),
				new byte[] { 1 })), 16);
		byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII),
				new byte[] { 1 })), 12);
		return new byte[][] { cek, nonce };
	}

	public static KeyPair generateKeyPair() throws GeneralSecurityException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp256r1"));
		return generator.generateKeyPair();
	}

	static byte[] ecdh(PrivateKey privateKey, ECPublicKey publicKey) throws GeneralSecurityException {
		KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
		agreement.init(privateKey);
		agreement.doPhase(publicKey, true);
		return agreement.generateSecret();
	}

	/** Punto sin comprimir: 0x04 || X (32 bytes) || Y (32 bytes). */
	public static byte[] encodePublic(ECPublicKey key) {
		byte[] result = new byte[65];
		result[0] = 4;
		copyUnsigned(key.getW().getAffineX(), result, 1);
		copyUnsigned(key.getW().getAffineY(), result, 33);
		return result;
	}

	public static ECPublicKey decodePublic(byte[] uncompressed) throws GeneralSecurityException {
		if (uncompressed.length != 65 || uncompressed[0] != 4) {
			throw new GeneralSecurityException("La clave pública P-256 debe tener 65 bytes y empezar por 0x04");
		}
		ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
				new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
		return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
	}

	public static byte[] encodePrivate(ECPrivateKey key) {
		byte[] result = new byte[32];
		copyUnsigned(key.getS(), result, 0);
		return result;
	}

	public static ECPrivateKey decodePrivate(byte[] raw) throws GeneralSecurityException {
		if (raw.length != 32) {
			throw new GeneralSecurityException("La clave privada P-256 debe tener 32 bytes");
		}
		return (ECPrivateKey) KeyFactory.getInstance("EC")
				.generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), P256));
	}

	public static String base64Url(byte[] bytes) {
		return B64_ENCODER.encodeToString(bytes);
	}

	/** Acepta Base64 URL con o sin relleno, como lo envían los navegadores. */
	public static byte[] fromBase64Url(String value) {
		return B64_DECODER.decode(value.trim().replace("=", ""));
	}

	static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		return mac.doFinal(data);
	}

	private static byte[] concat(byte[]... parts) {
		int length = 0;
		for (byte[] part : parts) {
			length += part.length;
		}
		ByteBuffer buffer = ByteBuffer.allocate(length);
		for (byte[] part : parts) {
			buffer.put(part);
		}
		return buffer.array();
	}

	/** Copia un entero sin signo en exactamente 32 bytes (BigInteger puede traer un 0 de más o de menos). */
	private static void copyUnsigned(BigInteger value, byte[] target, int offset) {
		byte[] bytes = value.toByteArray();
		int start = Math.max(0, bytes.length - 32);
		int length = bytes.length - start;
		System.arraycopy(bytes, start, target, offset + 32 - length, length);
	}

	private static ECParameterSpec p256() {
		try {
			return ((ECPublicKey) generateKeyPair().getPublic()).getParams();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("El JDK no soporta la curva P-256", e);
		}
	}

}
