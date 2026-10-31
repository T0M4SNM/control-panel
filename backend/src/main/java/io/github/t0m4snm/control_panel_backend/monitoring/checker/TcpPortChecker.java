package io.github.t0m4snm.control_panel_backend.monitoring.checker;

import java.net.InetSocketAddress;
import java.net.Socket;

import org.springframework.stereotype.Component;

import io.github.t0m4snm.control_panel_backend.monitoring.CheckType;

/** Éxito si el puerto acepta la conexión TCP; se cierra enseguida sin enviar nada. */
@Component
public class TcpPortChecker implements Checker {

	@Override
	public CheckType type() {
		return CheckType.TCP_PORT;
	}

	@Override
	public CheckOutcome check(CheckSpec spec) {
		long start = System.nanoTime();
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(spec.host(), spec.port()), (int) spec.timeout().toMillis());
			return CheckOutcome.ok(CheckOutcome.elapsedMs(start));
		} catch (Exception e) {
			return CheckOutcome.failure(CheckOutcome.elapsedMs(start), CheckOutcome.describe(e));
		}
	}

}
