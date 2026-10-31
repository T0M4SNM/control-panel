package io.github.t0m4snm.control_panel_backend.monitoring;

/** {@code UNKNOWN} hasta la primera comprobación con éxito o hasta alcanzar el umbral de fallos. */
public enum CheckStatus {
	UP,
	DOWN,
	UNKNOWN
}
