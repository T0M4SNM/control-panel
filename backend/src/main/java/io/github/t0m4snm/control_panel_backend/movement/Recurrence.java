package io.github.t0m4snm.control_panel_backend.movement;

/**
 * Cada cuánto se repite un movimiento. {@code MONTHLY} cuenta una vez al mes el mismo día que
 * {@code date} (el último día si el mes es más corto); {@code YEARLY}, una vez al año.
 */
public enum Recurrence {
	ONE_TIME,
	MONTHLY,
	YEARLY
}
