CREATE TABLE contract (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id   UUID          NOT NULL REFERENCES workspace(id),
    name           VARCHAR(150)  NOT NULL,
    provider       VARCHAR(100)  NOT NULL,
    type           VARCHAR(20)   NOT NULL CHECK (type IN ('SERVER', 'DOMAIN', 'OTHER')),
    amount         NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    billing_period VARCHAR(20)   NOT NULL CHECK (billing_period IN ('MONTHLY', 'YEARLY')),
    renewal_date   DATE          NOT NULL,
    auto_renew     BOOLEAN       NOT NULL DEFAULT TRUE,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELLED')),
    notes          TEXT
);

CREATE INDEX idx_contract_workspace ON contract(workspace_id);
CREATE INDEX idx_contract_renewal ON contract(renewal_date) WHERE status = 'ACTIVE';

CREATE TABLE server (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID         NOT NULL REFERENCES workspace(id),
    contract_id  UUID         REFERENCES contract(id) ON DELETE SET NULL,
    name         VARCHAR(150) NOT NULL,
    provider     VARCHAR(100),
    ip_address   VARCHAR(45),
    os           VARCHAR(100),
    cpu_cores    INTEGER      CHECK (cpu_cores > 0),
    ram_mb       INTEGER      CHECK (ram_mb > 0),
    disk_gb      INTEGER      CHECK (disk_gb > 0),
    notes        TEXT
);

CREATE INDEX idx_server_workspace ON server(workspace_id);

CREATE TABLE domain (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID         NOT NULL REFERENCES workspace(id),
    name         VARCHAR(255) NOT NULL,
    parent_id    UUID         REFERENCES domain(id) ON DELETE CASCADE,
    contract_id  UUID         REFERENCES contract(id) ON DELETE SET NULL,
    server_id    UUID         REFERENCES server(id) ON DELETE SET NULL,
    project_id   UUID         REFERENCES project(id) ON DELETE SET NULL,
    registrar    VARCHAR(100),
    notes        TEXT,
    UNIQUE (workspace_id, name),
    CHECK (parent_id IS NULL OR contract_id IS NULL)
);

CREATE INDEX idx_domain_project ON domain(project_id);
CREATE INDEX idx_domain_server ON domain(server_id);
CREATE INDEX idx_domain_parent ON domain(parent_id);

CREATE TABLE project_server (
    project_id         UUID         NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    server_id          UUID         NOT NULL REFERENCES server(id) ON DELETE CASCADE,
    cost_share_percent NUMERIC(5,2) NOT NULL DEFAULT 100
                       CHECK (cost_share_percent >= 0 AND cost_share_percent <= 100),
    PRIMARY KEY (project_id, server_id)
);

CREATE INDEX idx_project_server_server ON project_server(server_id);
