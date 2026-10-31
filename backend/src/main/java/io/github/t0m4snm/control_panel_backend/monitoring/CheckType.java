package io.github.t0m4snm.control_panel_backend.monitoring;

/**
 * Qué comprueba un {@link HealthCheck}:
 * <ul>
 * <li>{@code HTTP}: que una URL responde con 2xx o 3xx.</li>
 * <li>{@code SSL}: que el certificado de un host es válido y cuándo caduca.</li>
 * <li>{@code PING}: que un host responde (ICMP).</li>
 * <li>{@code TCP_PORT}: que un puerto de un host acepta conexiones.</li>
 * </ul>
 */
public enum CheckType {
	HTTP,
	SSL,
	PING,
	TCP_PORT
}
