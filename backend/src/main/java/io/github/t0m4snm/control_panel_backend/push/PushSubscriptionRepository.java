package io.github.t0m4snm.control_panel_backend.push;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

	Optional<PushSubscription> findByEndpoint(String endpoint);

	List<PushSubscription> findByUserIdIn(Collection<UUID> userIds);

	long countByUserId(UUID userId);

}
