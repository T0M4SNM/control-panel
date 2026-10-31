-- Líneas de log que envía Vector desde cada servidor (contenedores Docker y logs del sistema). Se purgan
-- a los 7 días: son muchas y solo sirven para ver qué está pasando ahora o qué pasó hace poco.
CREATE TABLE log_line (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    server_id   UUID         NOT NULL REFERENCES server(id) ON DELETE CASCADE,
    source      VARCHAR(200) NOT NULL,
    level       VARCHAR(10)  CHECK (level IN ('ERROR', 'WARN', 'INFO', 'DEBUG')),
    message     TEXT         NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_log_line_server_time ON log_line(server_id, occurred_at DESC, id DESC);
