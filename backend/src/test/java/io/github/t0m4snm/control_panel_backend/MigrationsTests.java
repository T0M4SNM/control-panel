package io.github.t0m4snm.control_panel_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprueba que las migraciones Flyway crean el esquema y que la base impone sus reglas.
 * Cada test se ejecuta en una transacción que se deshace al terminar.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MigrationsTests {

	@Autowired
	private JdbcTemplate jdbc;

	private UUID workspaceId;

	private UUID userId;

	private UUID projectId;

	@BeforeEach
	void setUp() {
		workspaceId = insert("INSERT INTO workspace (name) VALUES ('Pruebas') RETURNING id");
		userId = insertUser("admin@example.com", "ADMIN");
		projectId = insert("INSERT INTO project (workspace_id, name) VALUES (?, 'Proyecto') RETURNING id", workspaceId);
	}

	@Test
	void appliesAllMigrations() {
		List<String> versions = jdbc.queryForList(
				"SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);

		assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7");
	}

	@Test
	void createsAllTables() {
		List<String> tables = jdbc.queryForList("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
				""", String.class);

		assertThat(tables).containsExactlyInAnyOrder(
				"workspace", "app_user", "project", "project_member", "contract", "server", "domain",
				"project_server", "movement", "time_entry", "health_check", "check_result", "notification",
				"push_subscription", "ingest_key", "error_group", "error_event", "visit_salt", "visit_event");
	}

	@Test
	void rejectsUnknownEnumValue() {
		assertThatThrownBy(() -> insertUser("root@example.com", "ROOT"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsDuplicateEmail() {
		assertThatThrownBy(() -> insertUser("admin@example.com", "MEMBER"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsNegativeContractAmount() {
		assertThatThrownBy(() -> insertContract("-1.00"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void subdomainCannotHaveContract() {
		UUID contractId = insertContract("12.00");
		UUID parentId = insert("INSERT INTO domain (workspace_id, name, contract_id) VALUES (?, 'example.com', ?) RETURNING id",
				workspaceId, contractId);

		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO domain (workspace_id, name, parent_id, contract_id) VALUES (?, 'app.example.com', ?, ?)",
				workspaceId, parentId, contractId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsCostShareAbove100() {
		UUID serverId = insert("INSERT INTO server (workspace_id, name) VALUES (?, 'VPS') RETURNING id", workspaceId);

		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO project_server (project_id, server_id, cost_share_percent) VALUES (?, ?, 100.01)",
				projectId, serverId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void allowsOnlyOneRunningTimerPerUser() {
		insertTimeEntry("now() - interval '2 hours'", "now() - interval '1 hour'");
		insertTimeEntry("now() - interval '30 minutes'", null);

		assertThatThrownBy(() -> insertTimeEntry("now()", null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsTimeEntryEndingBeforeStart() {
		assertThatThrownBy(() -> insertTimeEntry("now()", "now() - interval '1 minute'"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void dedupeKeyIsUniquePerUser() {
		UUID memberId = insertUser("member@example.com", "MEMBER");
		insertNotification(userId, "check:1:down");
		insertNotification(memberId, "check:1:down");

		assertThatThrownBy(() -> insertNotification(userId, "check:1:down"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private UUID insert(String sql, Object... args) {
		return jdbc.queryForObject(sql, UUID.class, args);
	}

	private UUID insertUser(String email, String role) {
		return insert("""
				INSERT INTO app_user (workspace_id, email, password_hash, display_name, role)
				VALUES (?, ?, 'hash', 'Usuario', ?) RETURNING id
				""", workspaceId, email, role);
	}

	private UUID insertContract(String amount) {
		return insert("""
				INSERT INTO contract (workspace_id, name, provider, type, amount, billing_period, renewal_date)
				VALUES (?, 'Dominio', 'IONOS', 'DOMAIN', ?::numeric, 'YEARLY', current_date + 30) RETURNING id
				""", workspaceId, amount);
	}

	private void insertTimeEntry(String startedAt, String endedAt) {
		jdbc.update("INSERT INTO time_entry (user_id, project_id, started_at, ended_at) VALUES (?, ?, "
				+ startedAt + ", " + (endedAt == null ? "NULL" : endedAt) + ")", userId, projectId);
	}

	private void insertNotification(UUID recipientId, String dedupeKey) {
		jdbc.update("""
				INSERT INTO notification (user_id, type, severity, title, message, dedupe_key)
				VALUES (?, 'CHECK_DOWN', 'CRITICAL', 'Caída', 'El servicio no responde', ?)
				""", recipientId, dedupeKey);
	}

}
