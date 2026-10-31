CREATE TABLE health_check (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id         UUID         NOT NULL REFERENCES workspace(id),
    name                 VARCHAR(150) NOT NULL,
    type                 VARCHAR(20)  NOT NULL CHECK (type IN ('HTTP', 'SSL', 'PING', 'TCP_PORT')),
    target               VARCHAR(500) NOT NULL,
    port                 INTEGER      CHECK (port BETWEEN 1 AND 65535),
    server_id            UUID         REFERENCES server(id) ON DELETE SET NULL,
    domain_id            UUID         REFERENCES domain(id) ON DELETE SET NULL,
    project_id           UUID         REFERENCES project(id) ON DELETE SET NULL,
    interval_seconds     INTEGER      NOT NULL DEFAULT 300 CHECK (interval_seconds > 0),
    timeout_ms           INTEGER      NOT NULL DEFAULT 10000 CHECK (timeout_ms > 0),
    failure_threshold    INTEGER      NOT NULL DEFAULT 3 CHECK (failure_threshold > 0),
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    current_status       VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN'
                         CHECK (current_status IN ('UP', 'DOWN', 'UNKNOWN')),
    consecutive_failures INTEGER      NOT NULL DEFAULT 0,
    last_checked_at      TIMESTAMPTZ,
    status_changed_at    TIMESTAMPTZ
);

CREATE INDEX idx_health_check_workspace ON health_check(workspace_id);
CREATE INDEX idx_health_check_project ON health_check(project_id);

CREATE TABLE check_result (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    health_check_id UUID        NOT NULL REFERENCES health_check(id) ON DELETE CASCADE,
    checked_at      TIMESTAMPTZ NOT NULL,
    success         BOOLEAN     NOT NULL,
    response_ms     INTEGER,
    status_code     INTEGER,
    error           TEXT,
    ssl_expires_at  TIMESTAMPTZ
);

CREATE INDEX idx_check_result_check_time ON check_result(health_check_id, checked_at DESC);
CREATE INDEX idx_check_result_time ON check_result(checked_at);

CREATE TABLE notification (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    type            VARCHAR(30)  NOT NULL
                    CHECK (type IN ('CONTRACT_EXPIRING', 'CHECK_DOWN', 'CHECK_RECOVERED', 'SSL_EXPIRING')),
    severity        VARCHAR(20)  NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    title           VARCHAR(200) NOT NULL,
    message         TEXT         NOT NULL,
    contract_id     UUID         REFERENCES contract(id) ON DELETE CASCADE,
    health_check_id UUID         REFERENCES health_check(id) ON DELETE CASCADE,
    dedupe_key      VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at         TIMESTAMPTZ,
    UNIQUE (user_id, dedupe_key)
);

CREATE INDEX idx_notification_user_unread ON notification(user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX idx_notification_user ON notification(user_id, created_at DESC);

CREATE TABLE push_subscription (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    endpoint   TEXT         NOT NULL UNIQUE,
    p256dh     VARCHAR(255) NOT NULL,
    auth       VARCHAR(255) NOT NULL,
    user_agent VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_push_subscription_user ON push_subscription(user_id);
