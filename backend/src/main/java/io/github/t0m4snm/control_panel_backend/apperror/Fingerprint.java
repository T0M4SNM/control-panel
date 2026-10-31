package io.github.t0m4snm.control_panel_backend.apperror;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * «Huella» de un error para agrupar los que son el mismo: tipo + las primeras líneas de la pila sin
 * números de línea ni columna (cambian en cada despliegue). Sin pila, tipo + mensaje con los números
 * sustituidos («pedido 123 no existe» y «pedido 456 no existe» son el mismo error).
 */
public final class Fingerprint {

	static final int FRAMES = 5;

	/** Línea de pila de Node.js («at fn (file.js:10:5)») o de Java («at a.b.C.fn(C.java:10)»). */
	private static final Pattern FRAME = Pattern.compile("^\\s*at\\s+.+");

	private static final Pattern LINE_AND_COLUMN = Pattern.compile(":\\d+(:\\d+)?(?=\\)?$)");

	private static final Pattern NUMBERS = Pattern.compile("\\d+");

	private Fingerprint() {
	}

	public static String of(String type, String message, String stack) {
		List<String> frames = stack == null ? List.of()
				: stack.lines()
						.filter(line -> FRAME.matcher(line).matches())
						.limit(FRAMES)
						.map(line -> LINE_AND_COLUMN.matcher(line.trim()).replaceAll(""))
						.toList();
		String basis = frames.isEmpty()
				? type + "\n" + NUMBERS.matcher(message).replaceAll("#")
				: type + "\n" + String.join("\n", frames);
		return sha256(basis);
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
