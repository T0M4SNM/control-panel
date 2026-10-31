package io.github.t0m4snm.control_panel_backend.common;

import java.util.Map;
import java.util.StringJoiner;

/**
 * Escritura mínima de JSON para los pocos sitios que lo generan a mano (cuerpo de Web Push, contexto
 * de un error). Solo cadenas: no hace falta más y así no dependemos de cómo se configure Jackson.
 */
public final class Json {

	private Json() {
	}

	/** Cadena JSON con comillas y escapes. */
	public static String string(String value) {
		StringBuilder out = new StringBuilder("\"");
		for (char c : value.toCharArray()) {
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20) {
						out.append("\\u%04x".formatted((int) c));
					} else {
						out.append(c);
					}
				}
			}
		}
		return out.append('"').toString();
	}

	/** Objeto JSON con valores de texto, en el orden del mapa. */
	public static String object(Map<String, String> fields) {
		StringJoiner joiner = new StringJoiner(",", "{", "}");
		fields.forEach((key, value) -> joiner.add(string(key) + ":" + string(value)));
		return joiner.toString();
	}

}
