package io.github.t0m4snm.control_panel_backend.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CheckResultRepository extends JpaRepository<CheckResult, UUID> {

	List<CheckResult> findByHealthCheckIdOrderByCheckedAtDesc(UUID healthCheckId, Limit limit);

	/** Borrado en bloque con una sola sentencia, sin cargar los resultados en memoria. */
	@Modifying
	@Query("DELETE FROM CheckResult r WHERE r.checkedAt < :before")
	int deleteCheckedBefore(Instant before);

}
