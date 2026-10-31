package io.github.t0m4snm.control_panel_backend.apperror;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.project.ProjectService;

/**
 * Consulta de los errores de un proyecto: los ve quien ve el proyecto; marcarlos como resueltos, quien
 * puede editarlo.
 */
@Service
@Transactional
public class ErrorService {

	static final Duration EVENT_RETENTION = Duration.ofDays(30);

	static final int MAX_GROUPS = 100;

	static final int EVENTS_PER_GROUP = 20;

	private final ErrorGroupRepository groups;

	private final ErrorEventRepository events;

	private final ProjectService projects;

	private final Clock clock;

	public ErrorService(ErrorGroupRepository groups, ErrorEventRepository events, ProjectService projects, Clock clock) {
		this.groups = groups;
		this.events = events;
		this.projects = projects;
		this.clock = clock;
	}

	/** {@code status}: OPEN (por defecto), RESOLVED o null para todos. Del más reciente al más antiguo. */
	@Transactional(readOnly = true)
	public List<ErrorGroupResponse> list(AuthenticatedUser user, UUID projectId, ErrorGroup.Status status) {
		projects.findVisible(user, projectId);
		List<ErrorGroup> found = status == null
				? groups.findByProjectIdOrderByLastSeenAtDesc(projectId, Limit.of(MAX_GROUPS))
				: groups.findByProjectIdAndStatusOrderByLastSeenAtDesc(projectId, status, Limit.of(MAX_GROUPS));
		return found.stream().map(ErrorGroupResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public ErrorDetailResponse get(AuthenticatedUser user, UUID groupId) {
		ErrorGroup group = findVisible(user, groupId);
		return new ErrorDetailResponse(ErrorGroupResponse.from(group),
				events.findByGroupIdOrderByOccurredAtDesc(groupId, Limit.of(EVENTS_PER_GROUP)).stream()
						.map(ErrorEventResponse::from)
						.toList());
	}

	public ErrorGroupResponse resolve(AuthenticatedUser user, UUID groupId) {
		ErrorGroup group = findEditable(user, groupId);
		group.resolve(clock.instant());
		return ErrorGroupResponse.from(group);
	}

	public ErrorGroupResponse reopen(AuthenticatedUser user, UUID groupId) {
		ErrorGroup group = findEditable(user, groupId);
		group.reopen();
		return ErrorGroupResponse.from(group);
	}

	/** Borra las ocurrencias de más de 30 días; los grupos y sus contadores se quedan. */
	public int purgeOldEvents() {
		return events.deleteOccurredBefore(clock.instant().minus(EVENT_RETENTION));
	}

	private ErrorGroup findVisible(AuthenticatedUser user, UUID groupId) {
		ErrorGroup group = groups.findWithProject(groupId).orElseThrow(ApiErrors::notFound);
		projects.findVisible(user, group.getProject().getId());
		return group;
	}

	private ErrorGroup findEditable(AuthenticatedUser user, UUID groupId) {
		ErrorGroup group = groups.findWithProject(groupId).orElseThrow(ApiErrors::notFound);
		projects.findEditable(user, group.getProject().getId());
		return group;
	}

	public record ErrorGroupResponse(UUID id, UUID projectId, String projectName, String type, String message,
			ErrorGroup.Status status, long occurrences, Instant firstSeenAt, Instant lastSeenAt, Instant resolvedAt) {

		static ErrorGroupResponse from(ErrorGroup g) {
			return new ErrorGroupResponse(g.getId(), g.getProject().getId(), g.getProject().getName(), g.getType(),
					g.getMessage(), g.getStatus(), g.getOccurrences(), g.getFirstSeenAt(), g.getLastSeenAt(),
					g.getResolvedAt());
		}

	}

	/** @param context objeto JSON en texto, tal y como se guardó */
	public record ErrorEventResponse(UUID id, Instant occurredAt, String message, String stack, String environment,
			String release, String context) {

		static ErrorEventResponse from(ErrorEvent e) {
			return new ErrorEventResponse(e.getId(), e.getOccurredAt(), e.getMessage(), e.getStack(), e.getEnvironment(),
					e.getRelease(), e.getContext());
		}

	}

	public record ErrorDetailResponse(ErrorGroupResponse group, List<ErrorEventResponse> events) {
	}

}
