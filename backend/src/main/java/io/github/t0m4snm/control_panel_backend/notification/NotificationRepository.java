package io.github.t0m4snm.control_panel_backend.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Limit limit);

	List<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID userId, Limit limit);

	long countByUserIdAndReadAtIsNull(UUID userId);

	Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

	List<Notification> findByUserId(UUID userId);

	@Modifying
	@Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
	int markAllRead(UUID userId, Instant now);

}
