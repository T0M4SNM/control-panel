-- Visitas a las webs de tus proyectos, para la gráfica de usuarios activos. Sin cookies ni IP: cada
-- visitante es un hash de (sal del día + proyecto + IP + navegador). La sal se borra al acabar el día,
-- así que el hash no se puede deshacer ni seguir a nadie de un día para otro.
CREATE TABLE visit_salt (
    day  DATE PRIMARY KEY,
    salt BYTEA NOT NULL
);

-- VIEW: se abrió una página. PING: la pestaña sigue abierta y visible (cada minuto).
CREATE TABLE visit_event (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id    UUID         NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    visitor       VARCHAR(64)  NOT NULL,
    kind          VARCHAR(10)  NOT NULL CHECK (kind IN ('VIEW', 'PING')),
    path          VARCHAR(500),
    referrer_host VARCHAR(255),
    occurred_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_visit_event_project_time ON visit_event(project_id, occurred_at);
