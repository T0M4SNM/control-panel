-- Claves con las que tus apps y servidores envían datos al panel (errores, y más adelante
-- visitas y logs). Solo se guarda el hash SHA-256 de la clave; la clave en claro se enseña una vez.
CREATE TABLE ingest_key (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID         NOT NULL REFERENCES workspace(id),
    project_id   UUID         REFERENCES project(id) ON DELETE CASCADE,
    server_id    UUID         REFERENCES server(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    key_hash     VARCHAR(64)  NOT NULL UNIQUE,
    key_prefix   VARCHAR(12)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    CHECK (project_id IS NOT NULL OR server_id IS NOT NULL)
);

CREATE INDEX idx_ingest_key_project ON ingest_key(project_id);

-- Errores agrupados: las excepciones iguales (mismo tipo y misma pila) son un solo grupo.
CREATE TABLE error_group (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID         NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    fingerprint   VARCHAR(64)  NOT NULL,
    type          VARCHAR(200) NOT NULL,
    message       TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    occurrences   BIGINT       NOT NULL DEFAULT 0 CHECK (occurrences >= 0),
    first_seen_at TIMESTAMPTZ  NOT NULL,
    last_seen_at  TIMESTAMPTZ  NOT NULL,
    resolved_at   TIMESTAMPTZ,
    UNIQUE (project_id, fingerprint)
);

CREATE INDEX idx_error_group_project_last ON error_group(project_id, last_seen_at DESC);

-- Cada vez que ocurre un error. Se purgan a los 30 días; el grupo y su contador se quedan.
CREATE TABLE error_event (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id    UUID        NOT NULL REFERENCES error_group(id) ON DELETE CASCADE,
    occurred_at TIMESTAMPTZ NOT NULL,
    message     TEXT        NOT NULL,
    stack       TEXT,
    environment VARCHAR(50),
    release     VARCHAR(100),
    context     TEXT
);

CREATE INDEX idx_error_event_group_time ON error_event(group_id, occurred_at DESC);
CREATE INDEX idx_error_event_time ON error_event(occurred_at);

-- Nuevo tipo de aviso: error nuevo (o que vuelve) en una app
ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
    CHECK (type IN ('CONTRACT_EXPIRING', 'CHECK_DOWN', 'CHECK_RECOVERED', 'SSL_EXPIRING', 'APP_ERROR'));
ALTER TABLE notification ADD COLUMN error_group_id UUID REFERENCES error_group(id) ON DELETE CASCADE;
