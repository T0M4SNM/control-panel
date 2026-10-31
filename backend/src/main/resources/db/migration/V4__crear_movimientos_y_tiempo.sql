CREATE TABLE movement (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID          NOT NULL REFERENCES workspace(id),
    type         VARCHAR(20)   NOT NULL CHECK (type IN ('EXPENSE', 'INCOME')),
    amount       NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    date         DATE          NOT NULL,
    description  VARCHAR(500)  NOT NULL,
    project_id   UUID          REFERENCES project(id) ON DELETE SET NULL,
    contract_id  UUID          REFERENCES contract(id) ON DELETE SET NULL,
    recurrence   VARCHAR(20)   NOT NULL DEFAULT 'ONE_TIME'
                 CHECK (recurrence IN ('ONE_TIME', 'MONTHLY', 'YEARLY')),
    ends_on      DATE
);

CREATE INDEX idx_movement_workspace_date ON movement(workspace_id, date);
CREATE INDEX idx_movement_project ON movement(project_id);

CREATE TABLE time_entry (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    project_id  UUID         NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    started_at  TIMESTAMPTZ  NOT NULL,
    ended_at    TIMESTAMPTZ,
    hourly_rate NUMERIC(8,2) CHECK (hourly_rate >= 0),
    description VARCHAR(500),
    CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE INDEX idx_time_entry_project ON time_entry(project_id, started_at);
CREATE INDEX idx_time_entry_user ON time_entry(user_id, started_at);

-- Un usuario solo puede tener un cronómetro en marcha a la vez
CREATE UNIQUE INDEX uq_time_entry_running_per_user ON time_entry(user_id) WHERE ended_at IS NULL;
