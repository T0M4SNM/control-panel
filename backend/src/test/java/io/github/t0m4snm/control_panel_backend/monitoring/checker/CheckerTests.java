package io.github.t0m4snm.control_panel_backend.monitoring.checker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import io.github.t0m4snm.control_panel_backend.monitoring.CheckType;

/** Los ejecutores contra servidores locales levantados en el propio test, sin salir a internet. */
class CheckerTests {

	private static final Duration TIMEOUT = Duration.ofSeconds(2);

	private HttpServer http;

	@BeforeEach
	void startHttpServer() throws IOException {
		http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		http.createContext("/ok", exchange -> {
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		http.createContext("/caido", exchange -> {
			exchange.sendResponseHeaders(503, -1);
			exchange.close();
		});
		http.start();
	}

	@AfterEach
	void stopHttpServer() {
		http.stop(0);
	}

	@Test
	void httpSucceedsWith2xx() {
		CheckOutcome outcome = new HttpChecker().check(spec(CheckType.HTTP, url("/ok"), null));

		assertThat(outcome.success()).isTrue();
		assertThat(outcome.statusCode()).isEqualTo(200);
		assertThat(outcome.responseMs()).isNotNull();
	}

	@Test
	void httpFailsWith5xx() {
		CheckOutcome outcome = new HttpChecker().check(spec(CheckType.HTTP, url("/caido"), null));

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.statusCode()).isEqualTo(503);
		assertThat(outcome.error()).isEqualTo("HTTP 503");
	}

	@Test
	void httpFailsWhenNothingListens() throws IOException {
		int closedPort = freePort();
		CheckOutcome outcome = new HttpChecker().check(spec(CheckType.HTTP, "http://127.0.0.1:" + closedPort, null));

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.statusCode()).isNull();
		assertThat(outcome.error()).isNotBlank();
	}

	@Test
	void tcpPortSucceedsWhenSomethingListens() {
		CheckOutcome outcome = new TcpPortChecker()
				.check(spec(CheckType.TCP_PORT, "127.0.0.1", http.getAddress().getPort()));

		assertThat(outcome.success()).isTrue();
	}

	@Test
	void tcpPortFailsWhenNothingListens() throws IOException {
		CheckOutcome outcome = new TcpPortChecker().check(spec(CheckType.TCP_PORT, "127.0.0.1", freePort()));

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.error()).isNotBlank();
	}

	@Test
	void sslFailsAgainstAServerWithoutTls() {
		CheckOutcome outcome = new SslChecker().check(spec(CheckType.SSL, "127.0.0.1", http.getAddress().getPort()));

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.sslExpiresAt()).isNull();
		assertThat(outcome.error()).isNotBlank();
	}

	@Test
	void pingReachesLocalhost() {
		assertThat(new PingChecker().check(spec(CheckType.PING, "127.0.0.1", null)).success()).isTrue();
	}

	@Test
	void pingFailsForAnUnknownHost() {
		CheckOutcome outcome = new PingChecker().check(spec(CheckType.PING, "no-existe.invalid", null));

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.error()).contains("UnknownHostException");
	}

	@Test
	void longErrorsAreTruncated() {
		assertThat(CheckOutcome.failure(0, "x".repeat(2000)).error()).hasSize(500);
	}

	private String url(String path) {
		return "http://127.0.0.1:" + http.getAddress().getPort() + path;
	}

	private static CheckSpec spec(CheckType type, String target, Integer port) {
		return new CheckSpec(UUID.randomUUID(), type, target, port, TIMEOUT);
	}

	/** Un puerto que estaba libre: se abre y se cierra para que nadie escuche en él. */
	private static int freePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

}
