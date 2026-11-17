CREATE TABLE IF NOT EXISTS idempotency_key (
    key        TEXT PRIMARY KEY,
    run_id     TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
