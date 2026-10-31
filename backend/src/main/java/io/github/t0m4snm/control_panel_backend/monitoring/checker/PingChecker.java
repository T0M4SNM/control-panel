package io.github.t0m4snm.control_panel_backend.monitoring.checker;

import java.net.InetAddress;

import org.springframework.stereotype.Component;

import io.github.t0m4snm.control_panel_backend.monitoring.CheckType;

/**
 * Ping con {@link InetAddress#isReachable}. Java usa ICMP si el proceso puede (en Docker, el
 * contenedor necesita la capacidad {@code NET_RAW}, que Docker da por defecto); si no, prueba el
 * puerto TCP 7 y cuenta como vivo un host que rechaza la conexión.
 */
@Component
public class PingChecker implements Checker {

	@Override
	public CheckType type() {
		return CheckType.PING;
	}

	@Override
	public CheckOutcome check(CheckSpec spec) {
		long start = System.nanoTime();
		try {
			boolean reachable = InetAddress.getByName(spec.host()).isReachable((int) spec.timeout().toMillis());
			long elapsed = CheckOutcome.elapsedMs(start);
			return reachable ? CheckOutcome.ok(elapsed) : CheckOutcome.failure(elapsed, "Sin respuesta");
		} catch (Exception e) {
			return CheckOutcome.failure(CheckOutcome.elapsedMs(start), CheckOutcome.describe(e));
		}
	}

}
