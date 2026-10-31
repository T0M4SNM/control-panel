package io.github.t0m4snm.control_panel_backend.ingest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IngestKeyRepository extends JpaRepository<IngestKey, UUID> {

	@Query("SELECT k FROM IngestKey k LEFT JOIN FETCH k.project WHERE k.keyHash = :keyHash")
	Optional<IngestKey> findByKeyHash(String keyHash);

	List<IngestKey> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

	Optional<IngestKey> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

}
