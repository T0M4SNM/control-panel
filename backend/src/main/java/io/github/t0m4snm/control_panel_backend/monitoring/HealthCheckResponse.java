package io.github.t0m4snm.control_panel_backend.monitoring;

import java.time.Instant;
import java.util.UUID;

public record HealthCheckResponse(
		UUID id,
		String name,
		CheckType type,
		String target,
		Integer port,
		Ref server,
		Ref domain,
		Ref project,
		int intervalSeconds,
		int timeoutMs,
		int failureThreshold,
		boolean enabled,
		CheckStatus currentStatus,
		int consecutiveFailures,
		Instant lastCheckedAt,
		Instant statusChangedAt) {

	static HealthCheckResponse from(HealthCheck h) {
		return new HealthCheckResponse(h.getId(), h.getName(), h.getType(), h.getTarget(), h.getPort(),
				h.getServer() == null ? null : new Ref(h.getServer().getId(), h.getServer().getName()),
				h.getDomain() == null ? null : new Ref(h.getDomain().getId(), h.getDomain().getName()),
				h.getProject() == null ? null : new Ref(h.getProject().getId(), h.getProject().getName()),
				h.getIntervalSeconds(), h.getTimeoutMs(), h.getFailureThreshold(), h.isEnabled(),
				h.getCurrentStatus(), h.getConsecutiveFailures(), h.getLastCheckedAt(), h.getStatusChangedAt());
	}

	public record Ref(UUID id, String name) {
	}

	public record Result(Instant checkedAt, boolean success, Integer responseMs, Integer statusCode, String error,
			Instant sslExpiresAt) {

		static Result from(CheckResult r) {
			return new Result(r.getCheckedAt(), r.isSuccess(), r.getResponseMs(), r.getStatusCode(), r.getError(),
					r.getSslExpiresAt());
		}

	}

}
