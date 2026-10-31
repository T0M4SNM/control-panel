package io.github.t0m4snm.control_panel_backend.ingest;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de envíos por clave y minuto (ventana fija). Protege la base de datos de una app con un bucle
 * de errores o de una clave filtrada. En memoria: con una sola instancia del backend basta.
 *
 * @param <K> qué se limita: una clave de envío, una IP...
 */
public class RateLimiter<K> {

	private final int limitPerMinute;

	private final Clock clock;

	private final Map<K, Window> windows = new ConcurrentHashMap<>();

	public RateLimiter(int limitPerMinute, Clock clock) {
		this.limitPerMinute = limitPerMinute;
		this.clock = clock;
	}

	/** Cuenta {@code count} envíos de la clave; false si con ellos se pasa del límite de este minuto. */
	public boolean tryAcquire(K keyId, int count) {
		long minute = clock.millis() / 60_000;
		Window window = windows.compute(keyId,
				(id, current) -> current == null || current.minute != minute ? new Window(minute, count)
						: new Window(minute, current.used + count));
		// Limpieza de ventanas viejas, para que el mapa no crezca sin fin
		if (windows.size() > 1_000) {
			windows.values().removeIf(w -> w.minute < minute);
		}
		return window.used <= limitPerMinute;
	}

	private record Window(long minute, int used) {
	}

}
