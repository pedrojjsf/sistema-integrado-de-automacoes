-- SIA schema v1 (SQLite)
-- Regra: nada de dado sensivel. run/run_log guardam quem, quando e o que - nunca o conteudo.

CREATE TABLE IF NOT EXISTS plugin_state (
    id         TEXT PRIMARY KEY,
    version    TEXT NOT NULL,
    state      TEXT NOT NULL,
    loaded_at  INTEGER,
    error      TEXT
);

CREATE TABLE IF NOT EXISTS run (
    id            TEXT PRIMARY KEY,
    actor         TEXT NOT NULL,
    actor_name    TEXT,
    on_behalf_of  TEXT,
    plugin_id     TEXT NOT NULL,
    automation_id TEXT NOT NULL,
    status        TEXT NOT NULL,
    started_at    INTEGER NOT NULL,
    finished_at   INTEGER,
    trigger       TEXT,
    error_code    TEXT,
    params_keys   TEXT
);

CREATE TABLE IF NOT EXISTS run_log (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id     TEXT NOT NULL,
    ts         INTEGER NOT NULL,
    level      TEXT NOT NULL,
    event_code TEXT,
    message    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS schedule (
    id           TEXT PRIMARY KEY,
    trigger      TEXT NOT NULL,
    target       TEXT NOT NULL,
    enabled      INTEGER NOT NULL DEFAULT 1,
    next_fire_at INTEGER,
    owner        TEXT
);

CREATE TABLE IF NOT EXISTS schedule_fire (
    schedule_id TEXT NOT NULL,
    fire_time   INTEGER NOT NULL,
    PRIMARY KEY (schedule_id, fire_time)
);

CREATE TABLE IF NOT EXISTS workflow_run (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    status      TEXT NOT NULL,
    started_at  INTEGER NOT NULL,
    finished_at INTEGER
);

CREATE TABLE IF NOT EXISTS workflow_node (
    workflow_run_id TEXT NOT NULL,
    node_id         TEXT NOT NULL,
    status          TEXT NOT NULL,
    started_at      INTEGER,
    finished_at     INTEGER,
    outputs         TEXT,
    PRIMARY KEY (workflow_run_id, node_id)
);

CREATE TABLE IF NOT EXISTS kv_store (
    plugin_id  TEXT NOT NULL,
    key        TEXT NOT NULL,
    value      TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (plugin_id, key)
);

CREATE INDEX IF NOT EXISTS idx_run_plugin_started ON run (plugin_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_run_log_run ON run_log (run_id);
CREATE INDEX IF NOT EXISTS idx_schedule_next ON schedule (enabled, next_fire_at);
