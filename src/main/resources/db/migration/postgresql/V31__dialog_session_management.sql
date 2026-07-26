ALTER TABLE tb_dialog_session
    ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS pinned_at TIMESTAMP NULL;

UPDATE tb_dialog_session
SET last_activity_at = COALESCE(updated_at, started_at, created_at)
WHERE last_activity_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_dialog_session_resume
    ON tb_dialog_session (user_id, status, archived_at, last_activity_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_dialog_session_organize
    ON tb_dialog_session (user_id, archived_at, pinned_at DESC, last_activity_at DESC, id DESC);
