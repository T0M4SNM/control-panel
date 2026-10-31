package io.github.t0m4snm.control_panel_backend.monitoring.checker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Component;

import io.github.t0m4snm.control_panel_backend.monitoring.CheckType;

/** {@code GET} a la URL; éxito con 2xx o 3xx. Sigue las redirecciones salvo de https a http. */
@Component
public class HttpChecker implements Checker {

	private final HttpClient client = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	@Override
	public CheckType type() {
		return CheckType.HTTP;
	}

	@Override
	public CheckOutcome check(CheckSpec spec) {
		long start = System.nanoTime();
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(spec.target()))
					.timeout(spec.timeout())
					.header("User-Agent", "control-panel-monitor")
					.GET()
					.build();
			int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
			long elapsed = CheckOutcome.elapsedMs(start);
			boolean success = status >= 200 && status < 400;
			return new CheckOutcome(success, (int) elapsed, status, success ? null : "HTTP " + status, null);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return CheckOutcome.failure(CheckOutcome.elapsedMs(start), "Interrumpida");
		} catch (Exception e) {
			return CheckOutcome.failure(CheckOutcome.elapsedMs(start), CheckOutcome.describe(e));
		}
	}

}
