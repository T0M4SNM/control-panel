CREATE TABLE workspace (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID           NOT NULL REFERENCES workspace(id),
    email         VARCHAR(255)   NOT NULL UNIQUE,
    password_hash VARCHAR(255)   NOT NULL,
    display_name  VARCHAR(100)   NOT NULL,
    role          VARCHAR(20)    NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
    hourly_rate   NUMERIC(8,2)   CHECK (hourly_rate >= 0),
    active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_app_user_workspace ON app_user(workspace_id);
