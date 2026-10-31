package io.github.t0m4snm.control_panel_backend.apperror;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.t0m4snm.control_panel_backend.ingest.RateLimiter;

/** Agrupación de errores y límite de envíos, sin Spring. */
class FingerprintTests {

	private static final String NODE_STACK = """
			TypeError: Cannot read properties of undefined (reading 'id')
			    at getStock (/app/src/stock.js:42:17)
			    at Layer.handle (/app/node_modules/express/lib/router/layer.js:95:5)
			    at next (/app/node_modules/express/lib/router/route.js:149:13)
			""";

	@Test
	void sameErrorOnAnotherLineIsTheSameGroup() {
		String afterDeploy = NODE_STACK.replace("stock.js:42:17", "stock.js:57:9").replace("layer.js:95:5", "layer.js:96:5");

		assertThat(Fingerprint.of("TypeError", "a", NODE_STACK)).isEqualTo(Fingerprint.of("TypeError", "b", afterDeploy));
	}

	@Test
	void differentFunctionOrTypeIsAnotherGroup() {
		String otherFunction = NODE_STACK.replace("getStock", "saveStock");

		assertThat(Fingerprint.of("TypeError", "a", NODE_STACK)).isNotEqualTo(Fingerprint.of("TypeError", "a", otherFunction));
		assertThat(Fingerprint.of("TypeError", "a", NODE_STACK)).isNotEqualTo(Fingerprint.of("RangeError", "a", NODE_STACK));
	}

	@Test
	void withoutStackNumbersInTheMessageDoNotMatter() {
		assertThat(Fingerprint.of("Error", "Pedido 123 no existe", null))
				.isEqualTo(Fingerprint.of("Error", "Pedido 456 no existe", null))
				.isNotEqualTo(Fingerprint.of("Error", "Cliente 123 no existe", null));
	}

	@Test
	void javaStacksWorkToo() {
		String java = """
				java.lang.IllegalStateException: boom
					at com.example.Stock.load(Stock.java:10)
					at com.example.Api.get(Api.java:20)
				""";
		assertThat(Fingerprint.of("IllegalStateException", "boom", java))
				.isEqualTo(Fingerprint.of("IllegalStateException", "boom", java.replace(":10)", ":11)")));
	}

	@Test
	void rateLimiterResetsEveryMinute() {
		MutableClock clock = new MutableClock(Instant.parse("2026-10-31T10:00:05Z"));
		RateLimiter limiter = new RateLimiter(10, clock);
		UUID key = UUID.randomUUID();

		assertThat(limiter.tryAcquire(key, 8)).isTrue();
		assertThat(limiter.tryAcquire(key, 2)).isTrue();
		assertThat(limiter.tryAcquire(key, 1)).isFalse();
		assertThat(limiter.tryAcquire(UUID.randomUUID(), 10)).isTrue();

		clock.now = Instant.parse("2026-10-31T10:01:00Z");
		assertThat(limiter.tryAcquire(key, 10)).isTrue();
	}

	private static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

	}

}
