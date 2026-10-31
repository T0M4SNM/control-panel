CREATE TABLE project (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID         NOT NULL REFERENCES workspace(id),
    name         VARCHAR(150) NOT NULL,
    description  TEXT,
    status       VARCHAR(20)  NOT NULL DEFAULT 'IDEA'
                 CHECK (status IN ('IDEA', 'DEVELOPMENT', 'PRODUCTION', 'PAUSED')),
    repo_url     VARCHAR(500),
    public_url   VARCHAR(500),
    notes        TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_project_workspace ON project(workspace_id);

CREATE TABLE project_member (
    project_id UUID        NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role       VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER')),
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, user_id)
);

CREATE INDEX idx_project_member_user ON project_member(user_id);
