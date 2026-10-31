package io.github.t0m4snm.control_panel_backend.monitoring.checker;

import java.time.Duration;
import java.time.Instant;

/**
 * Resultado de ejecutar una comprobación.
 *
 * @param statusCode solo {@code HTTP}
 * @param sslExpiresAt solo {@code SSL}
 */
public record CheckOutcome(boolean success, Integer responseMs, Integer statusCode, String error,
		Instant sslExpiresAt) {

	private static final int MAX_ERROR_LENGTH = 500;

	public static CheckOutcome ok(long responseMs) {
		return new CheckOutcome(true, (int) responseMs, null, null, null);
	}

	public static CheckOutcome failure(long responseMs, String error) {
		return new CheckOutcome(false, (int) responseMs, null, truncate(error), null);
	}

	/** Mensaje legible de una excepción: muchas de red no traen mensaje, solo el tipo. */
	public static String describe(Throwable e) {
		String message = e.getMessage();
		return message == null || message.isBlank() ? e.getClass().getSimpleName()
				: e.getClass().getSimpleName() + ": " + message;
	}

	/** Milisegundos desde {@code startNanos} ({@link System#nanoTime()}). */
	static long elapsedMs(long startNanos) {
		return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
	}

	static String truncate(String error) {
		return error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
	}

}
