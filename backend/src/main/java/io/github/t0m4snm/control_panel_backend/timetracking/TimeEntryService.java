package io.github.t0m4snm.control_panel_backend.timetracking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.t0m4snm.control_panel_backend.auth.AuthenticatedUser;
import io.github.t0m4snm.control_panel_backend.common.ApiErrors;
import io.github.t0m4snm.control_panel_backend.common.Money;
import io.github.t0m4snm.control_panel_backend.project.Project;
import io.github.t0m4snm.control_panel_backend.project.ProjectService;
import io.github.t0m4snm.control_panel_backend.user.AppUser;
import io.github.t0m4snm.control_panel_backend.user.AppUserRepository;

/**
 * Cronómetro y entradas de tiempo.
 * <ul>
 * <li>Cada usuario registra su propio tiempo en proyectos donde es {@code OWNER} o {@code EDITOR}
 * (el admin, en cualquiera).</li>
 * <li>Un miembro solo ve sus entradas y nunca la tarifa ni el coste; el admin ve las de todos.</li>
 * <li>Iniciar el cronómetro con otro en marcha para el anterior, como en Toggl.</li>
 * </ul>
 */
@Service
@Transactional
public class TimeEntryService {

	static final int MAX_RANGE_DAYS = 366;

	private static final int DEFAULT_RANGE_DAYS = 30;

	private final TimeEntryRepository entries;

	private final AppUserRepository users;

	private final ProjectService projects;

	private final Clock clock;

	public TimeEntryService(TimeEntryRepository entries, AppUserRepository users, ProjectService projects,
			Clock clock) {
		this.entries = entries;
		this.users = users;
		this.projects = projects;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Optional<TimeEntryResponse> current(AuthenticatedUser user) {
		return entries.findRunning(user.id()).map(entry -> toResponse(user, entry));
	}

	public TimeEntryResponse start(AuthenticatedUser user, UUID projectId, String description) {
		Project project = projects.findForTimeTracking(user, projectId);
		Instant now = clock.instant();
		entries.findRunning(user.id()).ifPresent(running -> running.setEndedAt(now));
		// Se cierra el anterior antes de insertar, para no chocar con el índice de un solo cronómetro
		entries.flush();

		TimeEntry entry = new TimeEntry(currentUser(user), project, now);
		entry.setDescription(blankToNull(description));
		try {
			entries.saveAndFlush(entry);
		} catch (DataIntegrityViolationException e) {
			// Dos inicios casi a la vez: la base solo deja pasar uno
			throw ApiErrors.conflict("Ya hay un cronómetro en marcha");
		}
		return toResponse(user, entry);
	}

	public TimeEntryResponse stop(AuthenticatedUser user) {
		TimeEntry running = entries.findRunning(user.id()).orElseThrow(ApiErrors::notFound);
		running.setEndedAt(clock.instant());
		return toResponse(user, running);
	}

	/**
	 * Entradas que empiezan entre {@code from} y {@code to} (ambos incluidos, en la zona del usuario).
	 * Por defecto, los últimos 30 días. Un miembro solo puede ver las suyas.
	 */
	@Transactional(readOnly = true)
	public List<TimeEntryResponse> list(AuthenticatedUser user, LocalDate from, LocalDate to, UUID projectId,
			UUID userId) {
		if (!user.isAdmin() && userId != null && !userId.equals(user.id())) {
			throw ApiErrors.forbidden();
		}
		UUID ownerFilter = user.isAdmin() ? userId : user.id();
		LocalDate today = LocalDate.now(clock);
		LocalDate end = to == null ? today : to;
		LocalDate start = from == null ? end.minusDays(DEFAULT_RANGE_DAYS) : from;
		if (end.isBefore(start)) {
			throw ApiErrors.badRequest("La fecha final no puede ser anterior a la inicial");
		}
		if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
			throw ApiErrors.badRequest("El periodo no puede pasar de " + MAX_RANGE_DAYS + " días");
		}

		Instant fromInstant = start.atStartOfDay(clock.getZone()).toInstant();
		Instant toInstant = end.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
		return entries.findStartedBetween(user.workspaceId(), fromInstant, toInstant).stream()
				.filter(t -> ownerFilter == null || t.getUser().getId().equals(ownerFilter))
				.filter(t -> projectId == null || t.getProject().getId().equals(projectId))
				.map(t -> toResponse(user, t))
				.toList();
	}

	/** Entrada manual ya terminada. Para tiempo en curso se usa el cronómetro. */
	public TimeEntryResponse create(AuthenticatedUser user, TimeEntryRequest request) {
		if (request.endedAt() == null) {
			throw ApiErrors.badRequest("Una entrada manual necesita hora de fin; para tiempo en curso usa el cronómetro");
		}
		Project project = projects.findForTimeTracking(user, request.projectId());
		TimeEntry entry = new TimeEntry(currentUser(user), project, request.startedAt());
		apply(user, entry, request);
		return toResponse(user, entries.save(entry));
	}

	public TimeEntryResponse update(AuthenticatedUser user, UUID entryId, TimeEntryRequest request) {
		TimeEntry entry = findEditable(user, entryId);
		if (request.endedAt() == null && !entry.isRunning()) {
			throw ApiErrors.badRequest("Una entrada terminada no puede volver a estar en marcha");
		}
		if (!entry.getProject().getId().equals(request.projectId())) {
			entry.setProject(projects.findForTimeTracking(user, request.projectId()));
		}
		entry.setStartedAt(request.startedAt());
		apply(user, entry, request);
		return toResponse(user, entry);
	}

	public void delete(AuthenticatedUser user, UUID entryId) {
		entries.delete(findEditable(user, entryId));
	}

	private void apply(AuthenticatedUser user, TimeEntry entry, TimeEntryRequest request) {
		Instant now = clock.instant();
		if (request.startedAt().isAfter(now)) {
			throw ApiErrors.badRequest("No se puede registrar tiempo en el futuro");
		}
		if (request.endedAt() != null) {
			if (!request.endedAt().isAfter(request.startedAt())) {
				throw ApiErrors.badRequest("La hora de fin debe ser posterior a la de inicio");
			}
			if (request.endedAt().isAfter(now)) {
				throw ApiErrors.badRequest("No se puede registrar tiempo en el futuro");
			}
		}
		if (request.hourlyRate() != null) {
			if (!user.isAdmin()) {
				throw ApiErrors.forbidden();
			}
			entry.setHourlyRate(Money.toCents(request.hourlyRate()));
		}
		entry.setEndedAt(request.endedAt());
		entry.setDescription(blankToNull(request.description()));
	}

	/** El dueño o el admin; para los demás la entrada no existe. */
	private TimeEntry findEditable(AuthenticatedUser user, UUID entryId) {
		TimeEntry entry = entries.findInWorkspace(entryId, user.workspaceId()).orElseThrow(ApiErrors::notFound);
		if (!user.isAdmin() && !entry.getUser().getId().equals(user.id())) {
			throw ApiErrors.notFound();
		}
		return entry;
	}

	private AppUser currentUser(AuthenticatedUser user) {
		return users.findById(user.id()).orElseThrow(ApiErrors::notFound);
	}

	private TimeEntryResponse toResponse(AuthenticatedUser user, TimeEntry entry) {
		return TimeEntryResponse.from(entry, clock.instant(), user.isAdmin());
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
