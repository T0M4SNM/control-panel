package io.github.t0m4snm.control_panel_backend.monitoring;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckResultRepository extends JpaRepository<CheckResult, UUID> {

	List<CheckResult> findByHealthCheckIdOrderByCheckedAtDesc(UUID healthCheckId, Limit limit);

}
